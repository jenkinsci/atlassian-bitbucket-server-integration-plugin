package com.atlassian.bitbucket.jenkins.internal.trigger;

public interface BitbucketWebhookTrigger {

    void trigger(BitbucketWebhookTriggerRequest triggerRequest);

    /**
     * Is the trigger applicable for the given webhook. If the trigger is configured for RefChange and the event
     * is a PR opened event it should return false. The trigger should <em>NOT</em> trigger as a result of this call.
     *
     * @param event the webhook as it was received
     * @return true if this trigger is applicable to the given webhook
     *
     * @since 3.0.0
     */
    boolean isApplicableForEventType(AbstractWebhookEvent event);
}
