package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;

public class BitbucketBranchSCMHead extends BitbucketSCMHead {

    public BitbucketBranchSCMHead(BitbucketDefaultBranch branch) {
        super(branch.getDisplayId(), branch.getLatestCommit(), -1);
    }
}
