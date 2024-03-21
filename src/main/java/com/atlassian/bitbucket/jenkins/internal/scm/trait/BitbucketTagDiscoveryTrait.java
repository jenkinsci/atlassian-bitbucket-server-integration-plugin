package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketTagClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.scm.*;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class BitbucketTagDiscoveryTrait extends SCMSourceTrait {

    private static final Logger log = Logger.getLogger(BitbucketTagDiscoveryTrait.class.getName());

    @DataBoundConstructor
    public BitbucketTagDiscoveryTrait() {
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (context instanceof BitbucketSCMSourceContext) {
            BitbucketSCMSourceContext bitbucketContext = (BitbucketSCMSourceContext) context;

            BitbucketTagDiscoveryTrait.DescriptorImpl
                    descriptor = (BitbucketTagDiscoveryTrait.DescriptorImpl) getDescriptor();
            Optional<BitbucketClientFactory> clientFactory =
                    descriptor.getClientFactory(bitbucketContext);

            if (!clientFactory.isPresent()) {
                log.log(Level.WARNING, "Server configuration missing, cannot resolve client for Tag discovery");
                return;
            }

            BitbucketSCMRepository repository = bitbucketContext.getRepository();
            BitbucketTagClient bitbucketTagClient = clientFactory.get()
                    .getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug())
                    .getBitbucketTagClient(bitbucketContext.getTaskListener());

            bitbucketContext.withDiscoveryHandler(
                    new BitbucketSCMHeadDiscoveryHandler() {
                        @Override
                        public Stream<? extends SCMHead> discoverHeads() {
                            if (bitbucketContext.getEventHeads().isEmpty()) {
                                return bitbucketTagClient.getRemoteTags().map(BitbucketTagSCMHead::new);
                            }

                            return bitbucketContext.getEventHeads().stream()
                                    .filter(BitbucketTagSCMHead.class::isInstance);
                        }

                        @Override
                        public SCMRevision toRevision(SCMHead head) {
                            if (head instanceof BitbucketTagSCMHead) {
                                return new BitbucketSCMRevision((BitbucketTagSCMHead) head,
                                        ((BitbucketTagSCMHead) head).getLatestCommit());
                            }

                            RuntimeException e = new IllegalStateException("The specified head needs to be an " +
                                                                           "instance of BitbucketTagSCMHead");
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

        public Optional<BitbucketClientFactory> getClientFactory(BitbucketSCMSourceContext bitbucketContext) {
            return bitbucketPluginConfiguration.getServerById(bitbucketContext.getRepository().getServerId())
                    .map(BitbucketServerConfiguration::getBaseUrl)
                    .map(baseUrl -> bitbucketClientFactoryProvider.getClient(baseUrl,
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(bitbucketContext.getCredentials())));
        }

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return BitbucketSCMSourceContext.class;
        }

        @Override
        public String getDisplayName() {
            return Messages.bitbucket_scm_trait_discovery_tag_display();
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return BitbucketSCMSource.class;
        }
    }
}
