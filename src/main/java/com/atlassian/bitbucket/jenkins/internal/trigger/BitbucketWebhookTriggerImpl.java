package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.InstanceBasedNameGenerator;
import com.atlassian.bitbucket.jenkins.internal.scm.RetryingWebhookHandler;
import hudson.Extension;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.stream.Collectors.groupingBy;

public class BitbucketWebhookTriggerImpl extends Trigger<Job<?, ?>>
        implements BitbucketWebhookTrigger {

    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookTriggerImpl.class.getName());

    private transient BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private transient RetryingWebhookHandler webhookHandler;

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

    @Override
    public void start(Job<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        SCMTriggerItem triggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
        if (triggerItem == null) {
            return;
        } else {
            triggerItem.getSCMs()
                    .stream().filter(scm -> scm instanceof BitbucketSCM)
                    .map(scm -> (BitbucketSCM) scm)
                    .collect(groupingBy(this::getUniqueRepoSlug))
                    .entrySet()
                    .stream()
                    .flatMap(entry -> entry.getValue().stream())
                    .forEach(this::addTrigger);
        }
    }

    void setWebhookHandler(RetryingWebhookHandler retryingWebhookHandler) {
        this.webhookHandler = retryingWebhookHandler;
    }

    void setBitbucketPluginConfiguration(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    private void addTrigger(BitbucketSCM scm) {
        BitbucketServerConfiguration serverConfiguration = getServer(scm.getServerId());
        scm.getRepositories().forEach(repo -> registerWebhook(serverConfiguration, repo));
    }

    private BitbucketServerConfiguration getServer(String serverId) {
        return bitbucketPluginConfiguration
                .getServerById(serverId)
                .orElseThrow(() -> new RuntimeException("Server config not found"));
    }

    private void registerWebhook(
            BitbucketServerConfiguration serverConfiguration,
            BitbucketSCMRepository repository) {
        BitbucketWebhook webhook = webhookHandler.register(serverConfiguration, repository);
        LOGGER.info("Webhook registered -" + webhook);
    }

    private String getUniqueRepoSlug(BitbucketSCM scm) {
        return scm.getProjectKey() + "/" + scm.getRepositories();
    }

    @Extension
    @Symbol("BitbucketWebhookTriggerImpl")
    public static class BitbucketWebhookTriggerDescriptor extends TriggerDescriptor {

        // For now, the max threads is just a constant. In the future this may become configurable.
        private static final int MAX_THREADS = 10;

        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @SuppressWarnings("TransientFieldInNonSerializableClass")
        private final transient SequentialExecutionQueue queue;
        private final RetryingWebhookHandler retryingWebhookHandler;

        @SuppressWarnings("unused")
        public BitbucketWebhookTriggerDescriptor() {
            this(new SequentialExecutionQueue(
                    Executors.newFixedThreadPool(
                            MAX_THREADS,
                            new NamingThreadFactory(Executors.defaultThreadFactory(), "BitbucketWebhookTrigger"))));
        }

        public BitbucketWebhookTriggerDescriptor(SequentialExecutionQueue queue) {
            this.queue = queue;
            InstanceBasedNameGenerator instanceBasedNameGenerator =
                    new InstanceBasedNameGenerator(InstanceIdentity.get());
            retryingWebhookHandler =
                    new RetryingWebhookHandler(Jenkins.get().getRootUrl(),
                            bitbucketClientFactoryProvider,
                            instanceBasedNameGenerator,
                            new JenkinsToBitbucketCredentialsImpl());
        }

        @Override
        public String getDisplayName() {
            return Messages.BitbucketWebhookTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null;
        }

        @Override
        public BitbucketWebhookTriggerImpl newInstance(@Nullable StaplerRequest req,
                                                       JSONObject formData) throws FormException {
            BitbucketWebhookTriggerImpl trigger = (BitbucketWebhookTriggerImpl) super.newInstance(req, formData);
            trigger.setWebhookHandler(retryingWebhookHandler);
            trigger.setBitbucketPluginConfiguration(bitbucketPluginConfiguration);
            return trigger;
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
