package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

public class BitbucketWebhookMultibranchTrigger extends Trigger<MultiBranchProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookMultibranchTrigger.class.getName());

    @SuppressWarnings("RedundantNoArgConstructor") // Required for Stapler
    @DataBoundConstructor
    public BitbucketWebhookMultibranchTrigger() {
    }

    @Override
    public BitbucketWebhookMultibranchTrigger.DescriptorImpl getDescriptor() {
        return (BitbucketWebhookMultibranchTrigger.DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void start(MultiBranchProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        if (!newInstance) {
            return;
        }
        BitbucketWebhookMultibranchTrigger.DescriptorImpl descriptor = getDescriptor();
        project.getSCMSources().stream().filter(scm -> scm instanceof BitbucketSCMSource)
                .filter(scm -> ((BitbucketSCMSource) scm).isValid())
                .forEach(scm -> {
            boolean isAdded = descriptor.addTrigger(project, (BitbucketSCMSource) scm);
            ((BitbucketSCMSource) scm).setWebhookRegistered(isAdded);
        });
    }

    @Symbol("BitbucketWebhookMultibranchTriggerImpl")
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;

        @SuppressWarnings("unused")
        public DescriptorImpl() {

        }

        public DescriptorImpl(RetryingWebhookHandler webhookHandler,
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

        @VisibleForTesting
        boolean addTrigger(Item item, BitbucketSCMSource scm) {
            try {
                registerWebhook(item, scm.getBitbucketSCMRepository());
                return true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "There was a problem while trying to add webhook", ex);
                throw ex;
            }
        }

        private BitbucketServerConfiguration getServer(String serverId) {
            return bitbucketPluginConfiguration
                    .getServerById(serverId)
                    .orElseThrow(() -> new BitbucketClientException(
                            "Server config not found for input server id " + serverId));
        }

        private void registerWebhook(Item item, BitbucketSCMRepository repository) {
            requireNonNull(repository.getServerId());
            BitbucketServerConfiguration bitbucketServerConfiguration = getServer(repository.getServerId());

            BitbucketWebhook webhook = retryingWebhookHandler.register(
                    bitbucketServerConfiguration.getBaseUrl(),
                    bitbucketServerConfiguration.getGlobalCredentialsProvider(item),
                    repository);
            LOGGER.info("Webhook returned - " + webhook);
        }
    }
}
