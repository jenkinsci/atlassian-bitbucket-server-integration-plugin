package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.scm.PollingResult;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Level;
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
    public static final class BitbucketWebhookTriggerDescriptor extends TriggerDescriptor {

        // For now, the max threads is just a constant. In the future this may become configurable.
        private static final int MAX_THREADS = 10;

        @SuppressWarnings("TransientFieldInNonSerializableClass")
        private final transient SequentialExecutionQueue queue =
                new SequentialExecutionQueue(
                        Executors.newFixedThreadPool(
                                MAX_THREADS,
                                new NamingThreadFactory(
                                        Executors.defaultThreadFactory(),
                                        "BitbucketWebhookTrigger")));

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
            queue.execute(
                    new Runner(
                            job,
                            triggerItem,
                            new CauseAction(new BitbucketWebhookTriggerCause(triggerRequest)),
                            triggerRequest.getAdditionalActions()));
        }
    }

    private static class Runner implements Runnable {

        private static final Action[] ACTION_ARRAY = new Action[0];

        private final List<Action> actions = new ArrayList<>();
        @Nullable
        private final Job<?, ?> job;
        private final SCMTriggerItem triggerItem;

        private Runner(
                @Nullable Job<?, ?> job,
                SCMTriggerItem triggerItem,
                Action causeAction,
                List<Action> additionalActions) {
            actions.add(causeAction);
            actions.addAll(additionalActions);
            this.triggerItem = triggerItem;
            this.job = job;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Runner runner = (Runner) o;
            return Objects.equals(job, runner.job);
        }

        @Override
        public int hashCode() {
            return Objects.hash(job);
        }

        @Override
        public void run() {
            if (job == null) {
                return;
            }
            File logFile = new File(job.getRootDir(), "bitbucket-webhook-trigger.log");
            try {
                StreamTaskListener listener = new StreamTaskListener(logFile);

                long start = System.currentTimeMillis();
                PrintStream logger = listener.getLogger();
                logger.println(
                        "Starting polling: "
                                + DateFormat.getDateTimeInstance().format(new Date(start)));

                PollingResult result = triggerItem.poll(listener);
                logger.println(
                        "Poll complete. Took "
                                + Util.getTimeSpanString(System.currentTimeMillis() - start));

                if (result.hasChanges()) {
                    logger.println("Changes since last build: " + result.change);
                    triggerItem.scheduleBuild2(0, actions.toArray(ACTION_ARRAY));
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Failed to trigger job %s because an error occurred while writing the polling log to %s",
                        job,
                        logFile.getPath()),
                        e);
            }
        }
    }
}
