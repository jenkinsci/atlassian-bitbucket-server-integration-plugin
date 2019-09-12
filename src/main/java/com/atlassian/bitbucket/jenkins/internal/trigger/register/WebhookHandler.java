package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;

/**
 * Register a webhook to Bitbucket server if there is not already one.
 */
public interface WebhookHandler {

    /**
     * Registers webhooks
     *
     * @param request containing webhook related details
     * @return result of webhook registration.
     */
    BitbucketWebhook register(WebhookRegisterRequest request);
}
