package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.Extension;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class BitbucketWebhookTriggerImpl extends Trigger<Job<?, ?>>
        implements BitbucketWebhookTrigger {

    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookTriggerImpl.class.getName());

    @SuppressWarnings("RedundantNoArgConstructor") // Required for Stapler
    @DataBoundConstructor
    public BitbucketWebhookTriggerImpl() {
    }

    @Override
    public BitbucketWebhookTriggerDescriptor getDescriptor() {
        return (BitbucketWebhookTriggerDescriptor) super.getDescriptor();
    }

    @Override
    public void trigger(BitbucketWebhookTriggerRequest triggerRequest) {
        SCMTriggerItem triggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
        if (triggerItem == null) {
            // This shouldn't happen because of BitbucketWebhookTriggerDescriptor.isApplicable
            return;
        }
        getDescriptor().schedule(job, triggerItem, triggerRequest);
    }

    @Extension
    @Symbol("BitbucketWebhookTriggerImpl")
    public static class BitbucketWebhookTriggerDescriptor extends TriggerDescriptor {

        // For now, the max threads is just a constant. In the future this may become configurable.
        private static final int MAX_THREADS = 10;

        @SuppressWarnings("TransientFieldInNonSerializableClass")
        private final transient SequentialExecutionQueue queue;

        @SuppressWarnings("unused")
        public BitbucketWebhookTriggerDescriptor() {
            queue = new SequentialExecutionQueue(
                    Executors.newFixedThreadPool(
                            MAX_THREADS,
                            new NamingThreadFactory(Executors.defaultThreadFactory(), "BitbucketWebhookTrigger")));
        }

        public BitbucketWebhookTriggerDescriptor(SequentialExecutionQueue queue) {
            this.queue = queue;
        }

        @Override
        public String getDisplayName() {
            return Messages.BitbucketWebhookTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null;
        }

        public void schedule(
                @Nullable Job<?, ?> job,
                SCMTriggerItem triggerItem,
                BitbucketWebhookTriggerRequest triggerRequest) {
            CauseAction causeAction = new CauseAction(new BitbucketWebhookTriggerCause(triggerRequest));
            queue.execute(new BitbucketTriggerWorker(job, triggerItem, causeAction, triggerRequest.getAdditionalActions()));
        }
    }
}
