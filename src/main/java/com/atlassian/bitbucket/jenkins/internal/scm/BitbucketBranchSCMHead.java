package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefChange;

public class BitbucketBranchSCMHead extends BitbucketSCMHead {

    public BitbucketBranchSCMHead(BitbucketDefaultBranch branch) {
        super(branch.getDisplayId(), branch.getLatestCommit(), -1);
    }

    public BitbucketBranchSCMHead(BitbucketRefChange refChange) {
        super(refChange.getRef().getDisplayId(), refChange.getToHash(), -1);
    }
}
