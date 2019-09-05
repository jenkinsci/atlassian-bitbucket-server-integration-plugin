package com.atlassian.bitbucket.jenkins.internal.trigger;

public interface BitbucketWebhookTrigger {

    void trigger(BitbucketWebhookTriggerRequest triggerRequest);
}
