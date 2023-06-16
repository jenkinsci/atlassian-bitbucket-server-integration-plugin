package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.SCMHead;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMHead extends SCMHead {

    private final String latestCommit;
    private final long updatedDate;

    public BitbucketSCMHead(String name, String latestCommit, long updatedDate) {
        super(name);
        this.latestCommit = requireNonNull(latestCommit, "latestCommit");
        this.updatedDate = updatedDate;
    }

    public String getLatestCommit() {
        return latestCommit;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }
}
