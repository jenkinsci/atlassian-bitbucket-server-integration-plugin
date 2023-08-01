package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketBranchClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class BitbucketBranchDiscoveryTrait extends BitbucketSCMSourceTrait {

    private static final Logger log = Logger.getLogger(BitbucketBranchDiscoveryTrait.class.getName());

    @DataBoundConstructor
    public BitbucketBranchDiscoveryTrait() {
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (context instanceof BitbucketSCMSourceContext) {
            BitbucketSCMSourceContext bitbucketContext = (BitbucketSCMSourceContext) context;

            DescriptorImpl
                    descriptor = (DescriptorImpl) getDescriptor();
            Optional<BitbucketClientFactory> clientFactory =
                    descriptor.getClientFactory(bitbucketContext);

            if (!clientFactory.isPresent()) {
                log.log(Level.WARNING, "Server configuration missing, cannot resolve client for Branch discovery");
                return;
            }

            BitbucketSCMRepository repository = bitbucketContext.getRepository();
            BitbucketBranchClient bitbucketBranchClient = clientFactory.get()
                    .getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug())
                    .getBranchClient();

            bitbucketContext.withDiscoveryHandler(
                    new BitbucketSCMHeadDiscoveryHandler() {
                        @Override
                        public Stream<? extends SCMHead> discoverHeads() {
                            if (bitbucketContext.getEventHeads().isEmpty()) {
                                return
                                        bitbucketBranchClient.getRemoteBranches()
                                        .map(BitbucketBranchSCMHead::new);
                            }

                            return bitbucketContext.getEventHeads().stream()
                                    .filter(BitbucketBranchSCMHead.class::isInstance)
                                    .map(BitbucketBranchSCMHead.class::cast);
                        }

                        @Override
                        public SCMRevision toRevision(SCMHead head) {
                            if (head instanceof BitbucketBranchSCMHead) {
                                return new BitbucketSCMRevision((BitbucketBranchSCMHead) head,
                                        ((BitbucketBranchSCMHead) head).getLatestCommit());
                            }

                            RuntimeException e = new IllegalStateException("The specified head needs to be an " +
                                                                           "instance of BitbucketBranchSCMHead");
                            e.setStackTrace(new StackTraceElement[0]);
                            throw e;
                        }
                    });
        }
    }

    @Discovery
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        @Override
        public Class<? extends SCMBuilder> getBuilderClass() {
            return GitSCMBuilder.class;
        }

        @Override
        public String getDisplayName() {
            return Messages.bitbucket_scm_trait_discovery_branches_display();
        }

        public Optional<BitbucketClientFactory> getClientFactory(BitbucketSCMSourceContext bitbucketContext) {
            return bitbucketPluginConfiguration.getServerById(bitbucketContext.getRepository().getServerId())
                    .map(BitbucketServerConfiguration::getBaseUrl)
                    .map(baseUrl -> bitbucketClientFactoryProvider.getClient(baseUrl,
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(bitbucketContext.getCredentials())));
        }
    }
}
