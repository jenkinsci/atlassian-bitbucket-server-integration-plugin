package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProviderModule;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.Guice;
import hudson.Extension;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static jenkins.triggers.SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem;
import static org.apache.commons.lang3.StringUtils.isEmpty;

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
        if (triggerItem != null) {
            getDescriptor().schedule(job, triggerItem, triggerRequest);
        }
    }

    @Override
    public void start(Job<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        if (skipWebhookRegistration(project, newInstance)) {
            return;
        }
        SCMTriggerItem triggerItem = asSCMTriggerItem(job);
        if (triggerItem != null) {
            BitbucketWebhookTriggerDescriptor descriptor = getDescriptor();
            triggerItem.getSCMs()
                    .stream()
                    .filter(scm -> scm instanceof BitbucketSCM)
                    .map(scm -> (BitbucketSCM) scm)
                    .filter(scm -> !scm.isWebhookRegistered())
                    .filter(scm -> !checkTriggerExists(descriptor, scm))
                    .forEach(scm -> {
                        boolean isAdded = descriptor.addTrigger(project, scm);
                        scm.setWebhookRegistered(isAdded);
                    });
        }
    }

    private boolean checkTriggerExists(BitbucketWebhookTriggerDescriptor descriptor,
                                       BitbucketSCM scm) {
        boolean isExists = descriptor.webhookExists(job, scm);
        if (isExists) {
            scm.setWebhookRegistered(true);
        }
        return isExists;
    }

    /**
     * Returns true if the registration should be skipped. The entire point of skipping registration is to avoid
     * excessive remote calls to bitbucket server every time some configuration is changed.
     *
     * For pipeline job, this is invoked every time a build is run.
     *
     * We can't always continue registration check if newInstance is false since during Jenkin startup, this is invoked
     * for every job. Making remote call to Bitbucket server would make the startup slow.
     *
     * @param project     the input project
     * @param newInstance if this is invoked as part of new item configuration
     * @return true if registration should be skip.
     */
    boolean skipWebhookRegistration(Job<?, ?> project, boolean newInstance) {
        return !newInstance && !(project instanceof WorkflowJob);
    }

    @Symbol("BitbucketWebhookTriggerImpl")
    @Extension
    public static class BitbucketWebhookTriggerDescriptor extends TriggerDescriptor {

        // For now, the max threads is just a constant. In the future this may become configurable.
        private static final int MAX_THREADS = 10;

        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        private transient JenkinsProvider jenkinsProvider;

        @SuppressWarnings("TransientFieldInNonSerializableClass")
        private final transient SequentialExecutionQueue queue;

        @SuppressWarnings("unused")
        public BitbucketWebhookTriggerDescriptor() {
            this.queue = createSequentialQueue();
        }

        public BitbucketWebhookTriggerDescriptor(SequentialExecutionQueue queue,
                                                 RetryingWebhookHandler webhookHandler,
                                                 JenkinsProvider jenkinsProvider,
                                                 BitbucketPluginConfiguration bitbucketPluginConfiguration) {
            this.queue = queue;
            this.retryingWebhookHandler = webhookHandler;
            this.jenkinsProvider = jenkinsProvider;
            this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        }

        @Override
        public String getDisplayName() {
            return Messages.BitbucketWebhookTrigger_displayname();
        }

        @Override
        public boolean isApplicable(Item item) {
            return asSCMTriggerItem(item) != null;
        }

        @Override
        public Trigger<?> newInstance(@Nullable StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        @Inject
        public void setJenkinsProvider(JenkinsProvider jenkinsProvider) {
            this.jenkinsProvider = jenkinsProvider;
        }

        public void schedule(
                @Nullable Job<?, ?> job,
                SCMTriggerItem triggerItem,
                BitbucketWebhookTriggerRequest triggerRequest) {
            CauseAction causeAction = new CauseAction(new BitbucketWebhookTriggerCause(triggerRequest));
            queue.execute(new BitbucketTriggerWorker(job, triggerItem, causeAction, triggerRequest.getAdditionalActions()));
        }

        private boolean addTrigger(Item item, BitbucketSCM scm) {
            try {
                scm.getRepositories().forEach(repo -> registerWebhook(item, repo));
                return true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "There was a problem while trying to add webhook", ex);
                throw ex;
            }
        }

        private static SequentialExecutionQueue createSequentialQueue() {
            return new SequentialExecutionQueue(
                    Executors.newFixedThreadPool(
                            MAX_THREADS,
                            new NamingThreadFactory(Executors.defaultThreadFactory(), "BitbucketWebhookTrigger")));
        }

        private void registerWebhook(Item item, BitbucketSCMRepository repository) {
            requireNonNull(repository.getServerId());
            BitbucketServerConfiguration bitbucketServerConfiguration = getServer(repository.getServerId());

            BitbucketWebhook webhook = retryingWebhookHandler.register(
                    bitbucketServerConfiguration.getBaseUrl(),
                    bitbucketServerConfiguration.getGlobalCredentialsProvider(item),
                    repository);
            LOGGER.info("Webhook returned -" + webhook);
        }

        private boolean webhookExists(Job<?, ?> project, BitbucketSCM input) {
            try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
                if (jenkinsProvider == null) {
                    Guice.createInjector(new JenkinsProviderModule()).injectMembers(this);
                }
                return jenkinsProvider
                        .get().getAllItems(ParameterizedJobMixIn.ParameterizedJob.class)
                        .stream()
                        .filter(item -> !item.equals(project))
                        .filter(BitbucketWebhookTriggerDescriptor::isTriggerEnabled)
                        .map(item -> asSCMTriggerItem(item))
                        .filter(Objects::nonNull)
                        .map(scmItem -> scmItem.getSCMs())
                        .flatMap(Collection::stream)
                        .filter(scm -> scm instanceof BitbucketSCM)
                        .map(scm -> ((BitbucketSCM) scm).getRepositories())
                        .flatMap(Collection::stream)
                        .anyMatch(scm -> isExistingWebhookOnRepo(input, scm));
            }
        }

        private static boolean isTriggerEnabled(ParameterizedJobMixIn.ParameterizedJob job) {
            return job.getTriggers()
                    .values()
                    .stream()
                    .anyMatch(v -> v instanceof BitbucketWebhookTriggerImpl);
        }

        private boolean isExistingWebhookOnRepo(BitbucketSCM scm, BitbucketSCMRepository repository) {
            return scm.isWebhookRegistered() &&
                   scm.getRepositories().stream().allMatch(r -> r.getServerId().equals(repository.getServerId()) &&
                                                                r.getProjectKey().equals(repository.getProjectKey()) &&
                                                                r.getRepositorySlug().equals(repository.getRepositorySlug()) &&
                                                                !isMirrorConfigurationDifferent(r));
        }

        private boolean isMirrorConfigurationDifferent(BitbucketSCMRepository r) {
            return isEmpty(r.getMirrorName()) ^ isEmpty(r.getMirrorName());
        }

        private BitbucketServerConfiguration getServer(String serverId) {
            return bitbucketPluginConfiguration
                    .getServerById(serverId)
                    .orElseThrow(() -> new BitbucketClientException(
                            "Server config not found for input server id" + serverId));
        }
    }
}
