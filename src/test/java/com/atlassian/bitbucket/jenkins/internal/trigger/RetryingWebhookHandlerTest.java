package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.util.Collections.emptySet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private GlobalCredentialsProvider globalCredentialsProvider;

    @Before
    public void setup() {
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);
        InstanceBasedNameGenerator instanceBasedNameGenerator = mockWebhookNameGenerator();
        JenkinsProvider jenkinsProvider = mock(JenkinsProvider.class);
        retryingWebhookHandler =
                new RetryingWebhookHandler(
                        jenkinsProvider,
                        provider,
                        instanceBasedNameGenerator,
                        jenkinsToBitbucketCredentials
                );
        Jenkins jenkins = mock(Jenkins.class);
        when(jenkinsProvider.get()).thenReturn(jenkins);
        when(jenkins.getRootUrl()).thenReturn(BITBUCKET_BASE_URL);

        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(JOB_CREDENTIALS)).thenReturn(jobCredentials);

        BitbucketTokenCredentials globalAdminJenkinsCredentials = mock(BitbucketTokenCredentials.class);
        globalCredentialsProvider = () -> Optional.of(globalAdminJenkinsCredentials);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminJenkinsCredentials)).thenReturn(globalAdminCredentials);

        BitbucketClientFactory factory = mock(BitbucketClientFactory.class);
        when(provider.getClient(any(String.class), any(BitbucketCredentials.class))).thenReturn(factory);
        bitbucketWebhookClient = mockWebhookClient(factory);
    }

    @Test
    public void testCorrectOrderOfRetries() {
        BitbucketWebhook t = new BitbucketWebhook(1, WEBHOOK_NAME, emptySet(), "", true);

        when(bitbucketWebhookClient.registerWebhook(any(BitbucketWebhookRequest.class)))
                .thenThrow(AuthorizationException.class)
                .thenReturn(t);

        BitbucketWebhook r =
                retryingWebhookHandler.register(BITBUCKET_BASE_URL, globalCredentialsProvider, createSCMRepository());

        assertThat(r, is(t));
        InOrder inOrder = Mockito.inOrder(provider);
        inOrder.verify(provider).getClient(BITBUCKET_BASE_URL, globalAdminCredentials);
        inOrder.verify(provider).getClient(BITBUCKET_BASE_URL, jobCredentials);
    }

    @Test(expected = WebhookRegistrationFailed.class)
    public void testFailures() {
        when(bitbucketWebhookClient.registerWebhook(any(BitbucketWebhookRequest.class))).thenThrow(AuthorizationException.class);
        retryingWebhookHandler.register(BITBUCKET_BASE_URL, globalCredentialsProvider, createSCMRepository());
    }

    @Test
    public void testSuccessfulWebhookRegistrationUsingJobCredentials() {
        BitbucketWebhook t = new BitbucketWebhook(1, WEBHOOK_NAME, emptySet(), "", true);
        BitbucketSCMRepository bitbucketSCMRepository = createSCMRepository();
        when(bitbucketWebhookClient.registerWebhook(any(BitbucketWebhookRequest.class))).thenReturn(t);

        retryingWebhookHandler.register(BITBUCKET_BASE_URL, globalCredentialsProvider, bitbucketSCMRepository);

        verify(bitbucketWebhookClient).registerWebhook(argThat((BitbucketWebhookRequest request) -> request.getName().equals(WEBHOOK_NAME)));
    }

    private BitbucketSCMRepository createSCMRepository() {
        return new BitbucketSCMRepository(JOB_CREDENTIALS, "", PROJECT, PROJECT, REPO, REPO, SERVER_ID, "");
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
