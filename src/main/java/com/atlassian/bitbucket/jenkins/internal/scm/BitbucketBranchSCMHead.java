package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.Branch;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;

public class BitbucketBranchSCMHead extends BitbucketSCMHead {

    public BitbucketBranchSCMHead(@NonNull Branch branch) {
        super(branch.getName(), branch.getSHA1String(), 0);
    }
}
