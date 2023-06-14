package com.atlassian.bitbucket.jenkins.internal.scm;

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

                // The BitbucketPullRequestSCMHead uses the PR id as the head name so we need to use a custom
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
            bitbucketContext.withDiscoveryHandler(
                    new BitbucketSCMHeadDiscoveryHandler() {
                        @Override
                        public Stream<SCMHead> discoverHeads() {
                            return bitbucketContext.repositoryClient()
                                    .getPullRequests(BitbucketPullRequestState.OPEN)
                                    .map(BitbucketPullRequestSCMHead::new);
                        }

                        @Override
                        public SCMRevision toRevision(SCMHead head) {
                            if (head instanceof BitbucketPullRequestSCMHead) {
                                return new BitbucketPullRequestSCMRevision((BitbucketPullRequestSCMHead) head);
                            }

                            throw new IllegalStateException("Invalid head type " + head);
                        }
                    });
        }
    }

    @Discovery
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public Class<? extends SCMBuilder> getBuilderClass() {
            return GitSCMBuilder.class;
        }

        @Override
        public String getDisplayName() {
            return Messages.bitbucket_scm_trait_discovery_pullrequest_display();
        }
    }
}
