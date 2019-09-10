package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
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
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static jenkins.triggers.SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem;

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
        SCMTriggerItem triggerItem = asSCMTriggerItem(job);
        if (triggerItem == null) {
            // This shouldn't happen because of BitbucketWebhookTriggerDescriptor.isApplicable
            return;
        }
        getDescriptor().schedule(job, triggerItem, triggerRequest);
    }

    @Override
    public void start(Job<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        if (!newInstance) {
            return;
        }
        SCMTriggerItem triggerItem = asSCMTriggerItem(job);
        if (triggerItem == null) {
            return;
        } else {
            BitbucketWebhookTriggerDescriptor descriptor = getDescriptor();
            triggerItem.getSCMs()
                    .stream()
                    .filter(scm -> scm instanceof BitbucketSCM)
                    .map(scm -> (BitbucketSCM) scm)
                    .forEach(scm -> descriptor.addTrigger(scm));
        }
    }

    private String getUniqueRepoSlug(BitbucketSCM scm) {
        return scm.getProjectKey() + "/" + scm.getRepositories();
    }

    @Symbol("BitbucketWebhookTriggerImpl")
    @Extension
    public static class BitbucketWebhookTriggerDescriptor extends TriggerDescriptor {

        // For now, the max threads is just a constant. In the future this may become configurable.
        private static final int MAX_THREADS = 10;

        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;

        @SuppressWarnings("TransientFieldInNonSerializableClass")
        private final transient SequentialExecutionQueue queue;

        @SuppressWarnings("unused")
        public BitbucketWebhookTriggerDescriptor() {
            this.queue = createSequentialQueue();
        }

        public BitbucketWebhookTriggerDescriptor(SequentialExecutionQueue queue,
                                                 RetryingWebhookHandler webhookHandler) {
            this.queue = queue;
            this.retryingWebhookHandler = webhookHandler;
        }

        @Override
        public String getDisplayName() {
            return Messages.BitbucketWebhookTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return asSCMTriggerItem(item) != null;
        }

        public void schedule(
                @Nullable Job<?, ?> job,
                SCMTriggerItem triggerItem,
                BitbucketWebhookTriggerRequest triggerRequest) {
            CauseAction causeAction = new CauseAction(new BitbucketWebhookTriggerCause(triggerRequest));
            queue.execute(new BitbucketTriggerWorker(job, triggerItem, causeAction, triggerRequest.getAdditionalActions()));
        }

        private void addTrigger(BitbucketSCM scm) {
            scm.getRepositories().forEach(repo -> registerWebhook(repo));
        }

        private static SequentialExecutionQueue createSequentialQueue() {
            return new SequentialExecutionQueue(
                    Executors.newFixedThreadPool(
                            MAX_THREADS,
                            new NamingThreadFactory(Executors.defaultThreadFactory(), "BitbucketWebhookTrigger")));
        }

        private void registerWebhook(BitbucketSCMRepository repository) {
            requireNonNull(repository.getServerId());
            BitbucketWebhook webhook = retryingWebhookHandler.register(repository);
            LOGGER.info("Webhook returned -" + webhook);
        }
    }
}
