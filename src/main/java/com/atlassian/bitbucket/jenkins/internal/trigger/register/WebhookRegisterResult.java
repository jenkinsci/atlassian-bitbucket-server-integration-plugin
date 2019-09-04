package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;

public class WebhookRegisterResult {

    private final BitbucketWebhook webhook;
    private final boolean success;
    private final boolean isAlreadyRegistered;

    private WebhookRegisterResult(BitbucketWebhook webhook, boolean success, boolean isAlreadyRegistered) {
        this.webhook = webhook;
        this.success = success;
        this.isAlreadyRegistered = isAlreadyRegistered;
    }

    public static WebhookRegisterResult aSuccess(BitbucketWebhook webhook) {
        return new WebhookRegisterResult(webhook, true, false);
    }

    public static WebhookRegisterResult alreadyExists(BitbucketWebhook webhook) {
        return new WebhookRegisterResult(webhook, false, true);
    }

    public BitbucketWebhook getWebhook() {
        return webhook;
    }

    public boolean isAlreadyRegistered() {
        return isAlreadyRegistered;
    }

    public boolean isNewlyAdded() {
        return success;
    }
}
