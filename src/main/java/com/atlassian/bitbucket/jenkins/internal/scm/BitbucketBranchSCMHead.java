package com.atlassian.bitbucket.jenkins.internal.scm;

import org.eclipse.jgit.lib.ObjectId;

import java.util.Map.Entry;

public class BitbucketBranchSCMHead extends BitbucketSCMHead {


    public BitbucketBranchSCMHead(Entry<String, ObjectId> branch) {
        super(branch.getKey().replaceAll("refs/heads/", ""), branch.getValue().getName(), -1);
    }
}
