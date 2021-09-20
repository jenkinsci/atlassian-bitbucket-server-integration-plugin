package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.InvisibleAction;

import javax.annotation.CheckForNull;

public class BitbucketSCMRevisionAction extends InvisibleAction {

    public static final String REF_PREFIX = "refs/heads/";

    private final BitbucketSCMRepository bitbucketSCMRepository;
    private final String branchName;
    private final String revisionSha1;

    public BitbucketSCMRevisionAction(BitbucketSCMRepository bitbucketSCMRepository, @CheckForNull String branchName,
                                      String revisionSha1) {
        this.bitbucketSCMRepository = bitbucketSCMRepository;
        this.branchName = branchName;
        this.revisionSha1 = revisionSha1;
    }

    public BitbucketSCMRepository getBitbucketSCMRepo() {
        return bitbucketSCMRepository;
    }

    public String getRevisionSha1() {
        return revisionSha1;
    }

    @CheckForNull
    public String getBranchAsRefFormat() {
        return branchName != null ? REF_PREFIX + branchName : null;
    }
}
