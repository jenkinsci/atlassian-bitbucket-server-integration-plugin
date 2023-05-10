package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;

import java.io.Serializable;

public class MinimalPullRequest implements Serializable {

    private final String fromRefDisplayId;
    private final String fromRefId;
    private final long pullRequestId;
    private final String toRefDisplayId;
    private final String toRefId;

    public MinimalPullRequest(BitbucketPullRequest pullRequest) {
        this.fromRefId = pullRequest.getFromRef().getId();
        this.fromRefDisplayId = pullRequest.getFromRef().getDisplayId();
        this.pullRequestId = pullRequest.getId();
        this.toRefId = pullRequest.getToRef().getId();
        this.toRefDisplayId = pullRequest.getToRef().getDisplayId();
    }

    public String getFromRefDisplayId() {
        return fromRefDisplayId;
    }

    public String getFromRefId() {
        return fromRefId;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public String getToRefDisplayId() {
        return toRefDisplayId;
    }

    public String getToRefId() {
        return toRefId;
    }
}
