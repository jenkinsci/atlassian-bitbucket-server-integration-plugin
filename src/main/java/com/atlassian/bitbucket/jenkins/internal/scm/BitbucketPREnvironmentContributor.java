package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMHead;

@Extension
public class BitbucketPREnvironmentContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@NonNull Run r, @NonNull EnvVars envs, @NonNull TaskListener listener) {
        buildEnvironmentFor(r.getParent(), envs, listener);
    }

    public void buildEnvironmentFor(@NonNull Job job, @NonNull EnvVars envs, @NonNull TaskListener listener) {
        ItemGroup parent = job.getParent();
        if (parent instanceof MultiBranchProject) {
            BranchProjectFactory projectFactory = ((MultiBranchProject) parent).getProjectFactory();
            if (projectFactory.isProject(job)) {
                Branch branch = projectFactory.getBranch(job);
                SCMHead head = branch.getHead();
                if (head instanceof BitbucketPullRequestSCMHead) {
                    BitbucketPullRequestSCMHead prHead = (BitbucketPullRequestSCMHead) head;
                    envs.put("BRANCH_NAME", prHead.getOriginName());
                }
            }
        }
    }
}
