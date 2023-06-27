package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Stream;

public class BitbucketPullRequestDiscoveryTrait extends BitbucketSCMSourceTrait {

    @DataBoundConstructor
    public BitbucketPullRequestDiscoveryTrait() {
    }

    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        if (builder instanceof GitSCMBuilder) {
            GitSCMBuilder<?> gitSCMBuilder = (GitSCMBuilder<?>) builder;
            SCMRevision revision = gitSCMBuilder.revision();

            if (revision instanceof BitbucketPullRequestSCMRevision) {
                BitbucketPullRequestSCMRevision prRevision = (BitbucketPullRequestSCMRevision) revision;
                BitbucketPullRequestSCMHead prHead = (BitbucketPullRequestSCMHead) prRevision.getHead();

                // The BitbucketPullRequestSCMHead uses the PR id as the head name, so we need to use a custom
                // refspec to be able to map to the correct PR refs
                gitSCMBuilder.withRefSpec("+refs/heads/" + prHead.getOriginName() +
                        ":refs/remotes/@{remote}/" + prHead.getName());

                // Use the merge checkout strategy if the head specifies it
                if (prHead.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                    BitbucketSCMRevision targetRevision = (BitbucketSCMRevision) prRevision.getTarget();
                    SCMHead targetHead = targetRevision.getHead();
                    gitSCMBuilder.withExtension(new MergeWithGitSCMExtension(targetHead.getName(),
                            targetRevision.getCommitHash()));
                }
            }
        }
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (context instanceof BitbucketSCMSourceContext) {
            BitbucketSCMSourceContext bitbucketContext = (BitbucketSCMSourceContext) context;

            DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
            Optional<BitbucketClientFactory> clientFactory =
                    descriptor.getClientFactory(bitbucketContext);

            if (!clientFactory.isPresent()) {
                return;
            }

            BitbucketRepositoryClient repositoryClient = clientFactory.get()
                    .getProjectClient(bitbucketContext.getRepository().getProjectKey())
                    .getRepositoryClient(bitbucketContext.getRepository().getRepositorySlug());

            bitbucketContext.withDiscoveryHandler(
                    new BitbucketSCMHeadDiscoveryHandler() {
                        @Override
                        public Stream<? extends SCMHead> discoverHeads() {
                            if (bitbucketContext.getEventHeads().isEmpty()) {
                                return repositoryClient
                                        .getPullRequests(BitbucketPullRequestState.OPEN)
                                        .map(BitbucketPullRequestSCMHead::new)
                                        .filter(this::isSameOrigin); // We currently do not support forked PRs;
                            }

                            return bitbucketContext.getEventHeads().stream()
                                    .filter(BitbucketPullRequestSCMHead.class::isInstance)
                                    .map(BitbucketPullRequestSCMHead.class::cast)
                                    .filter(this::isSameOrigin); // We currently do not support forked PRs;
                        }

                        @Override
                        public SCMRevision toRevision(SCMHead head) {
                            if (head instanceof BitbucketPullRequestSCMHead) {
                                return new BitbucketPullRequestSCMRevision((BitbucketPullRequestSCMHead) head);
                            }

                            RuntimeException e = new IllegalStateException("The specified head needs to be an " +
                                    "instance of BitbucketPullRequestSCMHead");
                            e.setStackTrace(new StackTraceElement[0]);
                            throw e;
                        }

                        private boolean isSameOrigin(BitbucketPullRequestSCMHead head) {
                            MinimalPullRequest pullRequest = head.getPullRequest();
                            return pullRequest.getFromRepositoryId() == pullRequest.getToRepositoryId();
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
