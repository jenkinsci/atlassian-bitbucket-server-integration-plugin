package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;
import com.cloudbees.plugins.credentials.Credentials;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.BITBUCKET_BASE_URL;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.PROJECT;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.REPO;
import static java.util.Collections.emptySet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RetryingWebhookHandlerTest {

    private static final String JOB_CREDENTIALS = "job_cred";
    private static final String SERVER_ID = "serverid";
    private static final String WEBHOOK_NAME = "webhook";

    private BitbucketWebhookClient bitbucketWebhookClient;
    @Mock
    private BitbucketClientFactoryProvider provider;
    @Mock
    private BitbucketCredentials jobCredentials;
    @Mock
    private BitbucketCredentials globalCredentials;
    @Mock
    private BitbucketCredentials globalAdminCredentials;
    private RetryingWebhookHandler retryingWebhookHandler;

    @Before
    public void setup() {
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);
        BitbucketPluginConfiguration bitbucketPluginConfiguration = mock(BitbucketPluginConfiguration.class);
        InstanceBasedNameGenerator instanceBasedNameGenerator = mockWebhookNameGenerator();
        JenkinsProvider jenkinsProvider = mock(JenkinsProvider.class);
        retryingWebhookHandler =
                new RetryingWebhookHandler(
                        jenkinsProvider,
                        provider,
                        instanceBasedNameGenerator,
                        jenkinsToBitbucketCredentials,
                        bitbucketPluginConfiguration);
        Jenkins jenkins = mock(Jenkins.class);
        when(jenkinsProvider.get()).thenReturn(jenkins);
        when(jenkins.getRootUrl()).thenReturn(BITBUCKET_BASE_URL);

        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(JOB_CREDENTIALS)).thenReturn(jobCredentials);

        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        BitbucketTokenCredentials globalAdminJenkinsCredentials = mock(BitbucketTokenCredentials.class);
        Credentials globalCredentials = mock(Credentials.class);
        when(bitbucketPluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getAdminCredentials()).thenReturn(globalAdminJenkinsCredentials);
        when(serverConfiguration.getCredentials()).thenReturn(globalCredentials);

        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminJenkinsCredentials)).thenReturn(globalAdminCredentials);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(globalCredentials)).thenReturn(this.globalCredentials);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        BitbucketClientFactory factory = mock(BitbucketClientFactory.class);
        when(provider.getClient(any(String.class), any(BitbucketCredentials.class))).thenReturn(factory);
        bitbucketWebhookClient = mockWebhookClient(factory);
    }

    @Test
    public void testCorrectOrderOfRetries() {
        BitbucketWebhook t = new BitbucketWebhook(1, WEBHOOK_NAME, emptySet(), "", true);

        when(bitbucketWebhookClient.registerWebhook(any(BitbucketWebhookRequest.class)))
                .thenThrow(AuthorizationException.class)
                .thenThrow(AuthorizationException.class)
                .thenReturn(t);

        BitbucketWebhook r = retryingWebhookHandler.register(createSCMRepository());

        assertThat(r, is(equalTo(t)));
        InOrder inOrder = Mockito.inOrder(provider);
        inOrder.verify(provider).getClient(BITBUCKET_BASE_URL, globalAdminCredentials);
        inOrder.verify(provider).getClient(BITBUCKET_BASE_URL, jobCredentials);
        inOrder.verify(provider).getClient(BITBUCKET_BASE_URL, globalCredentials);
    }

    @Test(expected = WebhookRegistrationFailed.class)
    public void testFailures() {
        when(bitbucketWebhookClient.registerWebhook(any(BitbucketWebhookRequest.class))).thenThrow(AuthorizationException.class);
        retryingWebhookHandler.register(createSCMRepository());
    }

    @Test
    public void testSuccessfulWebhookRegistrationUsingJobCredentials() {
        BitbucketSCMRepository bitbucketSCMRepository = createSCMRepository();
        retryingWebhookHandler.register(bitbucketSCMRepository);

        verify(bitbucketWebhookClient).registerWebhook(argThat((BitbucketWebhookRequest request) -> request.getName().equals(WEBHOOK_NAME)));
    }

    private BitbucketSCMRepository createSCMRepository() {
        return new BitbucketSCMRepository(JOB_CREDENTIALS, PROJECT, PROJECT, REPO, REPO, SERVER_ID, false);
    }

    private BitbucketWebhookClient mockWebhookClient(BitbucketClientFactory clientFactory) {
        BitbucketWebhookClient bitbucketWebhookClient = mock(BitbucketWebhookClient.class);
        BitbucketProjectClient projectClient = mock(BitbucketProjectClient.class);
        BitbucketCapabilitiesClient client = mock(BitbucketCapabilitiesClient.class);
        when(clientFactory.getCapabilityClient()).thenReturn(client);
        when(clientFactory.getProjectClient(PROJECT)).thenReturn(projectClient);
        BitbucketRepositoryClient repositoryClient = mock(BitbucketRepositoryClient.class);
        when(projectClient.getRepositoryClient(REPO)).thenReturn(repositoryClient);
        when(repositoryClient.getWebhookClient()).thenReturn(bitbucketWebhookClient);
        return bitbucketWebhookClient;
    }

    private InstanceBasedNameGenerator mockWebhookNameGenerator() {
        InstanceBasedNameGenerator instanceBasedNameGenerator = mock(InstanceBasedNameGenerator.class);
        when(instanceBasedNameGenerator.getUniqueName()).thenReturn(WEBHOOK_NAME);
        return instanceBasedNameGenerator;
    }
}