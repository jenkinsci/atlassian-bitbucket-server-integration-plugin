package com.atlassian.bitbucket.jenkins.internal.trigger.register;

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
    WebhookRegisterResult register(WebhookRegisterRequest request);
}
