package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.InstanceBasedNameGenerator;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for testing integration of Webhook handler code with actual bitbucket server. Unfortunately,
 * test against mirror can't be performed as the bitbucket version we run the test against does not support mirrors.
 */
public class RetryingWebhookHandlerIT {

    private static final String JENKINS_URL = "http://localhost:8080/jenkins";
    private static final String JOB_CREDENTIAL_ID = "job_credentials";
    private static final String SERVER_ID = "123";
    private static final String WEBHOOK_NAME = RetryingWebhookHandlerIT.class.getSimpleName();

    private HttpRequestExecutor httpRequestExecutor = new HttpRequestExecutorImpl();
    private PersonalToken adminToken;
    private BitbucketCredentials adminCredentials;
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider =
            new BitbucketClientFactoryProvider(httpRequestExecutor);
    private BitbucketSCMRepository bitbucketSCMRepository;
    private PersonalToken nonAdminToken;
    private BitbucketCredentials nonAdminCredentials;
    private BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);

    @Before
    public void setup() {
        adminToken = createPersonalToken(BitbucketUtils.REPO_ADMIN_PERMISSION);
        nonAdminToken = createPersonalToken(BitbucketUtils.PROJECT_READ_PERMISSION);
        adminCredentials = JenkinsToBitbucketCredentialsImpl.getBearerCredentials(adminToken.getSecret());
        nonAdminCredentials = JenkinsToBitbucketCredentialsImpl.getBearerCredentials(nonAdminToken.getSecret());
        bitbucketSCMRepository =
                new BitbucketSCMRepository(JOB_CREDENTIAL_ID, PROJECT_NAME, PROJECT_KEY, REPO_NAME, REPO_SLUG, SERVER_ID, "");
        cleanWebhooks();
    }

    @After
    public void teardown() {
        cleanWebhooks();
        deletePersonalToken(adminToken.getId());
        deletePersonalToken(nonAdminToken.getId());
    }

    @Test
    public void testOneWebhookPerRepository() {
        RetryingWebhookHandler webhookHandler = getInstance(adminCredentials, adminCredentials, adminCredentials);

        BitbucketWebhook result1 = webhookHandler.register(bitbucketSCMRepository);
        BitbucketWebhook result2 = webhookHandler.register(bitbucketSCMRepository);

        assertThat(result1.getId(), is(equalTo(result2.getId())));
    }

    @Test
    public void testRegisterUsingFallbackCredentials() {
        RetryingWebhookHandler webhookHandler = getInstance(nonAdminCredentials, nonAdminCredentials, adminCredentials);

        BitbucketWebhook result = webhookHandler.register(bitbucketSCMRepository);

        assertThat(result.getUrl(), containsString(JENKINS_URL));
        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testWebhookRegister() {
        RetryingWebhookHandler webhookHandler = getInstance(adminCredentials, adminCredentials, adminCredentials);

        BitbucketWebhook result = webhookHandler.register(bitbucketSCMRepository);

        assertThat(result.getUrl(), containsString(JENKINS_URL));
        assertThat(result.getEvents(), iterableWithSize(1));
        assertThat(result.getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    private void cleanWebhooks() {
        bitbucketClientFactoryProvider.getClient(BITBUCKET_BASE_URL, adminCredentials)
                .getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getWebhookClient()
                .getWebhooks()
                .filter(bitbucketWebhook -> bitbucketWebhook.getName().startsWith(WEBHOOK_NAME))
                .map(BitbucketWebhook::getId)
                .forEach(id -> deleteWebhook(PROJECT_KEY, REPO_SLUG, id));
    }

    private RetryingWebhookHandler getInstance(BitbucketCredentials jobCredentials,
                                               BitbucketCredentials globalCredentials,
                                               BitbucketCredentials globalAdminCredentials) {
        BitbucketTokenCredentials c = mock(BitbucketTokenCredentials.class);
        when(serverConfiguration.getAdminCredentials()).thenReturn(c);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        JenkinsProvider jp = mock(JenkinsProvider.class);
        Jenkins j = mock(Jenkins.class);
        when(j.getRootUrl()).thenReturn(JENKINS_URL);
        when(jp.get()).thenReturn(j);

        InstanceBasedNameGenerator instanceBasedNameGenerator = mock(InstanceBasedNameGenerator.class);
        when(instanceBasedNameGenerator.getUniqueName()).thenReturn(WEBHOOK_NAME);

        JenkinsToBitbucketCredentials converter = mock(JenkinsToBitbucketCredentials.class);
        when(converter.toBitbucketCredentials(c)).thenReturn(globalAdminCredentials);
        when(converter.toBitbucketCredentials("adminCredentials")).thenReturn(globalAdminCredentials);
        when(converter.toBitbucketCredentials("credentialsId")).thenReturn(globalCredentials);
        when(converter.toBitbucketCredentials(JOB_CREDENTIAL_ID)).thenReturn(jobCredentials);

        BitbucketPluginConfiguration pluginConfiguration = mock(BitbucketPluginConfiguration.class);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(serverConfiguration));

        return new RetryingWebhookHandler(jp,
                bitbucketClientFactoryProvider,
                instanceBasedNameGenerator,
                converter,
                pluginConfiguration);
    }
}
