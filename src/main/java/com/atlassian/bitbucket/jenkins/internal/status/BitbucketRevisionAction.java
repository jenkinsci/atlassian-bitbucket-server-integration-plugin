package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.model.Action;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BitbucketRevisionAction implements Action {

    public static final String REF_PREFIX = "refs/heads/";

    private final BitbucketSCM bitbucketSCM;
    private final String branchName;
    private final String revisionSha1;
    private final String serverId;

    public BitbucketRevisionAction(BitbucketSCM bitbucketSCM, @Nullable String branchName, String revisionSha1,
                                   String serverId) {
        this.bitbucketSCM = bitbucketSCM;
        this.branchName = branchName;
        this.revisionSha1 = revisionSha1;
        this.serverId = serverId;
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

    public BitbucketSCM getBitbucketSCM() {
        return bitbucketSCM;
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

    public String getServerId() {
        return serverId;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
