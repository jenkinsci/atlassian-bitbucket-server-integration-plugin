package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.logging.Logger;

public class BitbucketWebhookMultibranchTrigger extends Trigger<MultiBranchProject<?, ?>> {

    //the version (of this class) where the PR trigger was introduced. Version is 0 based.
    private static final int BUILD_ON_PULL_REQUEST_VERSION = 1;
    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookMultibranchTrigger.class.getName());

    private final boolean pullRequestTrigger;
    private final boolean refTrigger;
    /**
     * This exists as a simple upgrade task. Old classes will de-serialise this to default value (of 0). New
     * classes will serialise the actual value that was stored. Because the constructor is not run during de-serialisation
     * we can safely set the value in the constructor to indicate which version this class is.
     *
     * @since 3.0.0
     */
    private final int version;

    @SuppressWarnings("RedundantNoArgConstructor") // Required for Stapler
    @DataBoundConstructor
    public BitbucketWebhookMultibranchTrigger(boolean pullRequestTrigger, boolean refTrigger) {
        this.refTrigger = refTrigger;
        this.pullRequestTrigger = pullRequestTrigger;
        version = BUILD_ON_PULL_REQUEST_VERSION;
    }

    @Override
    public BitbucketWebhookMultibranchTrigger.DescriptorImpl getDescriptor() {
        return (BitbucketWebhookMultibranchTrigger.DescriptorImpl) super.getDescriptor();
    }

    /**
     * Is the trigger applicable for the given webhook. If the trigger is configured for RefChange and the event
     * is a PR opened event it should return false. The trigger should <em>NOT</em> trigger as a result of this call.
     *
     * @param event the webhook as it was received
     * @return true if this trigger is applicable to the given webhook
     * @since 3.0.0
     */
    public boolean isApplicableForEventType(AbstractWebhookEvent event) {
        if (event instanceof PullRequestWebhookEvent) {
            if (isPullRequestTrigger()) {
                return event instanceof PullRequestOpenedWebhookEvent || event instanceof PullRequestFromRefUpdatedWebhookEvent;
            }
        } else if (event instanceof RefsChangedWebhookEvent) {
            return isRefTrigger();
        }
        return false; // No other events are applicable for triggers
    }

    public boolean isPullRequestTrigger() {
        return pullRequestTrigger;
    }

    public boolean isRefTrigger() {
        /*
         * If it is an old version we should return true as that is the old default.
         * If it is a new version we should use the value that was set.
         */
        if (version < BUILD_ON_PULL_REQUEST_VERSION) {
            return true;
        } else {
            return refTrigger;
        }
    }

    @Symbol("BitbucketWebhookMultibranchTrigger")
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;

        @SuppressWarnings("unused")
        public DescriptorImpl() {
        }

        @VisibleForTesting
        DescriptorImpl(RetryingWebhookHandler webhookHandler,
                       BitbucketPluginConfiguration bitbucketPluginConfiguration) {
            this.retryingWebhookHandler = webhookHandler;
            this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        }

        @Override
        public String getDisplayName() {
            return Messages.BitbucketWebhookMultibranchTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof MultiBranchProject;
        }
    }
}
