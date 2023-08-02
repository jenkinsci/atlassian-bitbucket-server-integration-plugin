package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.SCMHead;

import javax.annotation.CheckForNull;

public class BitbucketSCMHead extends SCMHead {

    private final String latestCommit;
    private final long updatedDate;

    public BitbucketSCMHead(String name, @CheckForNull String latestCommit, long updatedDate) {
        super(name);
        this.latestCommit = latestCommit;
        this.updatedDate = updatedDate;
    }

    @CheckForNull
    public String getLatestCommit() {
        return latestCommit;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }
}
