package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.AbstractWebhookEvent;
import hudson.Extension;
import hudson.model.Job;
import org.jenkinsci.Symbol;

public class SerializationFriendlyTrigger extends BitbucketWebhookTriggerImpl {

    private final transient BitbucketWebhookTriggerImpl delegate;

    public SerializationFriendlyTrigger(BitbucketWebhookTriggerImpl mockTrigger) {
        super(mockTrigger.isPullRequestTrigger(), mockTrigger.isRefTrigger());
        delegate = mockTrigger;
    }

    @Override
    public boolean isApplicableForEvent(AbstractWebhookEvent event) {
        return delegate.isApplicableForEvent(event);
    }

    @Override
    public void start(Job<?, ?> project, boolean newInstance) {
        delegate.start(project, newInstance);
    }

    @Override
    public void trigger(BitbucketWebhookTriggerRequest triggerRequest) {
        delegate.trigger(triggerRequest);
    }

    @Symbol("BitbucketWebhookTriggerImpl")
    @Extension
    public static class SerializationFriendlyTriggerDescriptor extends BitbucketWebhookTriggerImpl.BitbucketWebhookTriggerDescriptor {

    }
}
