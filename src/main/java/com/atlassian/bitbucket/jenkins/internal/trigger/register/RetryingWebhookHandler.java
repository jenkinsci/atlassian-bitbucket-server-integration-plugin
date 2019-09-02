package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;

/**
 * Admin permission is needed to add webhooks. It is possible that credentials in job configuration is not admin. This retries
 * adding webhook in with alternate credentials.
 */
public class RetryingWebhookHandler implements WebhookHandler {

    private final BitbucketClientFactoryProvider provider;
    private final String bitbucketBaseUrl;
    private final BitbucketCredentials jobCredentials;
    private final BitbucketCredentials globalCredentials;

    public RetryingWebhookHandler(
            BitbucketClientFactoryProvider provider, String bitbucketBaseUrl,
            BitbucketCredentials jobCredentials,
            BitbucketCredentials globalCredentials) {
        this.provider = provider;
        this.bitbucketBaseUrl = bitbucketBaseUrl;
        this.jobCredentials = jobCredentials;
        this.globalCredentials = globalCredentials;
    }

    @Override
    public WebhookRegisterResult register(WebhookRegisterRequest request) {
        try {
            return registerUsingCredentials(jobCredentials, request);
        } catch (AuthorizationException exception) {
            return registerUsingCredentials(globalCredentials, request);
        }
    }

    private WebhookRegisterResult registerUsingCredentials(BitbucketCredentials credentials,
                                                           WebhookRegisterRequest request) {
        BitbucketClientFactory clientFactory = provider.getClient(bitbucketBaseUrl, credentials);
        BitbucketCapabilitiesClient capabilityClient = clientFactory.getCapabilityClient();
        BitbucketWebhookClient webhookClient =
                clientFactory.getProjectClient(request.getProjectKey()).getRepositoryClient(request.getRepoSlug())
                        .getWebhookClient();
        WebhookHandler handler = new BitbucketWebhookHandler(capabilityClient, webhookClient);
        return handler.register(request);
    }
}