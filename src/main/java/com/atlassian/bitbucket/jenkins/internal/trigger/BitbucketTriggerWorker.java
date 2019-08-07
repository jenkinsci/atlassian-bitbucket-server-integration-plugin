package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.Util;
import hudson.model.Action;
import hudson.model.Job;
import hudson.scm.PollingResult;
import hudson.util.StreamTaskListener;
import jenkins.triggers.SCMTriggerItem;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketTriggerWorker implements Runnable {

    private static final Action[] ACTION_ARRAY = new Action[0];
    private static final Logger LOGGER = Logger.getLogger(BitbucketTriggerWorker.class.getName());
    private final List<Action> actions = new ArrayList<>();
    @CheckForNull
    private final Job<?, ?> job;
    private final SCMTriggerItem triggerItem;

    public BitbucketTriggerWorker(
            @CheckForNull Job<?, ?> job,
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
        BitbucketTriggerWorker runner = (BitbucketTriggerWorker) o;
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
            logger.println("Poll complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));

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

    public List<Action> getActions() {
        return Collections.unmodifiableList(actions);
    }

    @CheckForNull
    public Job<?, ?> getJob() {
        return job;
    }

    public SCMTriggerItem getTriggerItem() {
        return triggerItem;
    }
}

