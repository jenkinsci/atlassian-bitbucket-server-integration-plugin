package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.model.Cause;

import java.util.Objects;

import static com.atlassian.bitbucket.jenkins.internal.trigger.Messages.BitbucketWebhookTriggerCause_withAuthor;

public class BitbucketWebhookTriggerCause extends Cause {

    private final BitbucketWebhookTriggerRequest triggerRequest;

    public BitbucketWebhookTriggerCause(BitbucketWebhookTriggerRequest triggerRequest) {
        this.triggerRequest = triggerRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketWebhookTriggerCause that = (BitbucketWebhookTriggerCause) o;
        return Objects.equals(triggerRequest, that.triggerRequest);
    }

    @Override
    public String getShortDescription() {
        return triggerRequest
                .getActor()
                .map(actor -> BitbucketWebhookTriggerCause_withAuthor(actor.getDisplayName()))
                .orElseGet(Messages::BitbucketWebhookTriggerCause_withoutAuthor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(triggerRequest);
    }
}
