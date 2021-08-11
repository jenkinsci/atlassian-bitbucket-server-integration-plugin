package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;

import javax.annotation.Nullable;

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
    @Nullable
    BitbucketWebhook register(WebhookRegisterRequest request);
}
