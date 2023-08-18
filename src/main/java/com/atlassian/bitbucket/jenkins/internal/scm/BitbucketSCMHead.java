package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.SCMHead;

import javax.annotation.CheckForNull;

/**
 * @since 4.0.0
 */
public abstract class BitbucketSCMHead extends SCMHead {

    private final String latestCommit;
    private final long updatedDate;

    protected BitbucketSCMHead(String name, @CheckForNull String latestCommit, long updatedDate) {
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

    public abstract String getFullRef();
}
