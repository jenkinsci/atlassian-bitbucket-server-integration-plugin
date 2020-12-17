package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.provider.PullRequestStore;
import hudson.Extension;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;

public class PullRequestBranchStrategy extends BranchBuildStrategy {

    @DataBoundConstructor
    public  PullRequestBranchStrategy() {

    }

    @Override
    public boolean isAutomaticBuild(SCMSource source,
                                     SCMHead head,
                                     SCMRevision currRevision,
                                    SCMRevision prevRevision) {

        if (source instanceof BitbucketSCMSource){
            BitbucketSCMRepository bbsRepository = ((BitbucketSCMSource) source).getBitbucketSCMRepository();
            PullRequestStore pullRequestStore = ((DescriptorImpl) getDescriptor()).pullRequestStore;
            //logging
            return pullRequestStore.hasOpenPullRequests(head.getName(), bbsRepository);
        }
        return false;
    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        @Inject
        private PullRequestStore pullRequestStore;

        @Override
        public String getDisplayName() {
            return "Only build open pull requests";
        }


    }

}
