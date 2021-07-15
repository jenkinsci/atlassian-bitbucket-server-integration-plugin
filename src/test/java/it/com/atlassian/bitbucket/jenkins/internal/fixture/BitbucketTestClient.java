package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent;

/**
 * To make communicating with Bitbucket easier in tests.
 *
 * @since 3.0.0
 */
public class BitbucketTestClient {

    private final BitbucketCredentials adminToken;
    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketJenkinsRule bitbucketJenkinsRule;

    public BitbucketTestClient(BitbucketJenkinsRule bitbucketJenkinsRule) {
        this.bitbucketJenkinsRule = bitbucketJenkinsRule;
        JenkinsToBitbucketCredentialsImpl jenkinsToBitbucketCredentials = new JenkinsToBitbucketCredentialsImpl();
        adminToken = jenkinsToBitbucketCredentials.toBitbucketCredentials(bitbucketJenkinsRule.getAdminToken());
        bitbucketClientFactoryProvider = new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
    }

    /**
     * Check if the given webhook exists in the capability provided by the server. This has one big flaw in that
     * webhooks were released in 5.6 but the capability was only added in 6.6, thus this method can only be used
     * with certainty for webhooks newer than Bitbucket Server 6.6
     *
     * @param event event to check
     * @return true if the remote instance supports the webhook, with the caveat listed above
     */
    public boolean supportsWebhook(BitbucketWebhookEvent event) {
        try {
            return bitbucketClientFactoryProvider
                    .getClient(bitbucketJenkinsRule.getBitbucketServerConfiguration().getBaseUrl(), adminToken)
                    .getCapabilityClient()
                    .getWebhookSupportedEvents()
                    .getApplicationWebHooks()
                    .contains(event.getEventId());
        } catch (BitbucketMissingCapabilityException e) {
            return false; //this happens for Bitbucket versions prior to 6.6
        }
    }

    public BitbucketRepositoryClient getRepositoryClient(String projectKey, String repoSlug) {
        return bitbucketClientFactoryProvider
                .getClient(bitbucketJenkinsRule.getBitbucketServerConfiguration().getBaseUrl(), adminToken)
                .getProjectClient(projectKey)
                .getRepositoryClient(repoSlug);
    }

    public void removeAllWebHooks(String projectKey, String repoSlug) {
        BitbucketWebhookClient webhookClient = bitbucketClientFactoryProvider
                .getClient(bitbucketJenkinsRule.getBitbucketServerConfiguration().getBaseUrl(), adminToken)
                .getProjectClient(projectKey)
                .getRepositoryClient(repoSlug)
                .getWebhookClient();
        webhookClient.getWebhooks().forEach(hook -> webhookClient.deleteWebhook(hook.getId()));
    }
}
