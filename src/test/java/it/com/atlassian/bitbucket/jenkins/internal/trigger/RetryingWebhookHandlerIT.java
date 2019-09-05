package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BearerCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.InstanceBasedNameGenerator;
import com.atlassian.bitbucket.jenkins.internal.scm.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterResult;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for testing integration of Webhook handler code with actual bitbucket server. Unfortunately,
 * test against mirror can't be performed as the bitbucket version we run the test against does not support mirrors.
 */
public class RetryingWebhookHandlerIT {

    private static final String JENKINS_URL = "http://localhost:8080/jenkins";
    private static final String JOB_CREDENTIAL_ID = "job_credentials";
    private static final String WEBHOOK_NAME = RetryingWebhookHandlerIT.class.getSimpleName();

    private HttpRequestExecutor httpRequestExecutor = new HttpRequestExecutorImpl();
    private PersonalToken adminToken;
    private BitbucketCredentials adminCredentials;
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider =
            new BitbucketClientFactoryProvider(httpRequestExecutor);
    private BitbucketSCMRepository bitbucketSCMRepository;
    private PersonalToken nonAdminToken;
    private BitbucketCredentials nonAdminCredentials;

    @Before
    public void setup() {
        adminToken = createPersonalToken(BitbucketUtils.REPO_ADMIN_PERMISSION);
        nonAdminToken = createPersonalToken(BitbucketUtils.PROJECT_READ_PERMISSION);
        adminCredentials = new BearerCredentials(adminToken.getSecret());
        nonAdminCredentials = new BearerCredentials(nonAdminToken.getSecret());
        bitbucketSCMRepository = new BitbucketSCMRepository(JOB_CREDENTIAL_ID, PROJECT_KEY, REPO_SLUG, "123");
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

        WebhookRegisterResult result1 = webhookHandler.register(bitbucketSCMRepository, false);
        assertTrue(result1.isNewlyAdded());

        WebhookRegisterResult result2 = webhookHandler.register(bitbucketSCMRepository, false);
        assertFalse(result2.isNewlyAdded());
        assertTrue(result2.isAlreadyRegistered());
        assertThat(result1.getWebhook().getId(), is(equalTo(result2.getWebhook().getId())));
    }

    @Test
    public void testRegisterUsingFallbackCredentials() {
        RetryingWebhookHandler webhookHandler = getInstance(nonAdminCredentials, nonAdminCredentials, adminCredentials);

        WebhookRegisterResult result = webhookHandler.register(bitbucketSCMRepository, false);

        assertTrue(result.isNewlyAdded());
        assertThat(result.getWebhook().getUrl(), containsString(JENKINS_URL));
        assertThat(result.getWebhook().getEvents(), iterableWithSize(1));
        assertThat(result.getWebhook().getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testWebhookRegister() {
        RetryingWebhookHandler webhookHandler = getInstance(adminCredentials, adminCredentials, adminCredentials);

        WebhookRegisterResult result = webhookHandler.register(bitbucketSCMRepository, false);

        assertTrue(result.isNewlyAdded());
        assertThat(result.getWebhook().getUrl(), containsString(JENKINS_URL));
        assertThat(result.getWebhook().getEvents(), iterableWithSize(1));
        assertThat(result.getWebhook().getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
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
        BitbucketServerConfiguration configuration = mock(BitbucketServerConfiguration.class);
        BitbucketTokenCredentials c = mock(BitbucketTokenCredentials.class);
        when(configuration.getAdminCredentials()).thenReturn(c);

        InstanceBasedNameGenerator instanceBasedNameGenerator = mock(InstanceBasedNameGenerator.class);
        when(instanceBasedNameGenerator.getUniqueName()).thenReturn(WEBHOOK_NAME);

        JenkinsToBitbucketCredentials converter = mock(JenkinsToBitbucketCredentials.class);
        when(converter.toBitbucketCredentials(c)).thenReturn(globalAdminCredentials);
        when(converter.toBitbucketCredentials("adminCredentials")).thenReturn(globalAdminCredentials);
        when(converter.toBitbucketCredentials("credentialsId")).thenReturn(globalCredentials);
        when(converter.toBitbucketCredentials(JOB_CREDENTIAL_ID)).thenReturn(jobCredentials);

        return new RetryingWebhookHandler(JENKINS_URL,
                bitbucketClientFactoryProvider,
                configuration,
                instanceBasedNameGenerator,
                converter);
    }
}
