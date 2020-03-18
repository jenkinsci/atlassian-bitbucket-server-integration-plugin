package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import hudson.model.Action;

import javax.annotation.Nullable;
import java.util.*;

import static java.util.Optional.ofNullable;

public final class BitbucketWebhookTriggerRequest {

    private final BitbucketUser actor;
    private final List<Action> additionalActions;

    private BitbucketWebhookTriggerRequest(Builder builder) {
        actor = builder.actor;
        additionalActions = builder.additionalActions;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketWebhookTriggerRequest that = (BitbucketWebhookTriggerRequest) o;
        return Objects.equals(actor, that.actor) && Objects.equals(additionalActions, that.additionalActions);
    }

    public Optional<BitbucketUser> getActor() {
        return ofNullable(actor);
    }

    public List<Action> getAdditionalActions() {
        return additionalActions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, additionalActions);
    }

    public static final class Builder {

        private final List<Action> additionalActions = new ArrayList<>();
        private BitbucketUser actor;

        public Builder actor(@Nullable BitbucketUser value) {
            actor = value;
            return this;
        }

        public Builder additionalActions(Action... additionalActions) {
            this.additionalActions.addAll(Arrays.asList(additionalActions));
            return this;
        }

        public BitbucketWebhookTriggerRequest build() {
            return new BitbucketWebhookTriggerRequest(this);
        }
    }
}
