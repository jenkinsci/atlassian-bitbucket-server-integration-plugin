package com.atlassian.bitbucket.jenkins.internal.trigger;

public interface BitbucketWebhookTrigger {

    String TRIGGER_IDENTIFIER = "bitbucket_trigger_enabled";

    void trigger(BitbucketWebhookTriggerRequest triggerRequest);
}
