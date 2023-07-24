package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketGitClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class BitbucketBranchesDiscoveryTrait extends BitbucketSCMSourceTrait {
    private static final Logger log = Logger.getLogger(BitbucketBranchesDiscoveryTrait.class.getName());

    @DataBoundConstructor
    public BitbucketBranchesDiscoveryTrait() {
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
                log.log(Level.WARNING, "Server configuration missing, cannot resolve client for PR discovery");
                return;
            }

            BitbucketSCMSourceContext SCMContext = (BitbucketSCMSourceContext) context;
            BitbucketSCMRepository repository = bitbucketContext.getRepository();
            final BitbucketGitClient gitClient;
            try {
                String credentialsId = repository.getCredentialsId();
                if(credentialsId == null) {
                    log.log(Level.WARNING, "Credential ID was null");
                    return;
                }
                gitClient = clientFactory.get().getGitClient(SCMContext.getTaskListener(), credentialsId, SCMContext.getOwner());
            } catch (Exception e) {
                log.log(Level.WARNING, e.getMessage());
                return;
            }

            bitbucketContext.withDiscoveryHandler(
                    new BitbucketSCMHeadDiscoveryHandler() {
                        @Override
                        public Stream<? extends SCMHead> discoverHeads() {
                            if (bitbucketContext.getEventHeads().isEmpty()) {
                                return gitClient
                                        .getRemoteBranches()
                                        .stream()
                                        .map(BitbucketBranchSCMHead::new)
                                        .filter(this::isSameOrigin); // We currently do not support forked branches;
                            }

                            return bitbucketContext.getEventHeads().stream()
                                    .filter(BitbucketBranchSCMHead.class::isInstance)
                                    .map(BitbucketBranchSCMHead.class::cast)
                                    .filter(this::isSameOrigin); // We currently do not support forked branches;
                        }

                        @Override
                        public SCMRevision toRevision(SCMHead head) {
                            if (head instanceof BitbucketBranchSCMHead) {
                                return new BitbucketSCMRevision((BitbucketBranchSCMHead) head, ((BitbucketBranchSCMHead) head).getLatestCommit());
                            }

                            RuntimeException e = new IllegalStateException("The specified head needs to be an " +
                                                                           "instance of BitbucketPullRequestSCMHead");
                            e.setStackTrace(new StackTraceElement[0]);
                            throw e;
                        }

                        private boolean isSameOrigin(BitbucketBranchSCMHead head) {
                            return head.getOrigin() == SCMHeadOrigin.DEFAULT;
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
            return Messages.bitbucket_scm_trait_discovery_pullrequest_display();
        }

        public Optional<BitbucketClientFactory> getClientFactory(BitbucketSCMSourceContext bitbucketContext) {
            return bitbucketPluginConfiguration.getServerById(bitbucketContext.getRepository().getServerId())
                    .map(BitbucketServerConfiguration::getBaseUrl)
                    .map(baseUrl -> bitbucketClientFactoryProvider.getClient(baseUrl,
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(bitbucketContext.getCredentials())));
        }
    }
}
