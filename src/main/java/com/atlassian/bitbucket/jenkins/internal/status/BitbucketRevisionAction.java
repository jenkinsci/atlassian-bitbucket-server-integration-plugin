package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.Action;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BitbucketRevisionAction implements Action {

    public static final String REF_PREFIX = "refs/heads/";

    private final BitbucketSCMRepository bitbucketSCMRepository;
    private final String branchName;
    private final String revisionSha1;

    public BitbucketRevisionAction(BitbucketSCMRepository bitbucketSCMRepository, @Nullable String branchName,
                                   String revisionSha1) {
        this.bitbucketSCMRepository = bitbucketSCMRepository;
        this.branchName = branchName;
        this.revisionSha1 = revisionSha1;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    public BitbucketSCMRepository getBitbucketSCMRepo() {
        return bitbucketSCMRepository;
    }

    @CheckForNull
    public String getBranchName() {
        return branchName;
    }

    @CheckForNull
    public String getBranchAsRefFormat() {
        return branchName != null ? REF_PREFIX + branchName : null;
    }

    public String getRevisionSha1() {
        return revisionSha1;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
