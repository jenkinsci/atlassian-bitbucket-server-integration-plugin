package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.SCMHead;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMHead extends SCMHead {

    private final String latestCommit;

    public BitbucketSCMHead(String name, String latestCommit) {
        super(name);
        this.latestCommit = requireNonNull(latestCommit, "latestCommit");
    }

    public String getLatestCommit() {
        return latestCommit;
    }
}
