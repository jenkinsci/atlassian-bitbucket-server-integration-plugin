package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
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

public class BitbucketWebhookMultibranchPRTrigger extends Trigger<MultiBranchProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookMultibranchPRTrigger.class.getName());

    @SuppressWarnings("RedundantNoArgConstructor") // Required for Stapler
    @DataBoundConstructor
    public BitbucketWebhookMultibranchPRTrigger() {
    }

    @Override
    public BitbucketWebhookMultibranchPRTrigger.DescriptorImpl getDescriptor() {
        return (BitbucketWebhookMultibranchPRTrigger.DescriptorImpl) super.getDescriptor();
    }

    @Symbol("BbsPRTrigger")
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
            return Messages.BitbucketWebhookMultibranchPRTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof MultiBranchProject;
        }
    }
}

