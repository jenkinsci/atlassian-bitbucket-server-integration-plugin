package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;

import javax.annotation.CheckForNull;
import java.io.Serializable;

/**
 * @since 4.0.0
 */
public class MinimalPullRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String description;
    private final String fromLatestCommit;
    private final String fromRefDisplayId;
    private final String fromRefId;
    private final int fromRepositoryId;
    private final long pullRequestId;
    private final BitbucketPullRequestState state;
    private final String title;
    private final String toLatestCommit;
    private final String toRefDisplayId;
    private final String toRefId;
    private final int toRepositoryId;

    public MinimalPullRequest(BitbucketPullRequest pullRequest) {
        this.description = pullRequest.getDescription();
        this.fromLatestCommit = pullRequest.getFromRef().getLatestCommit();
        this.fromRefId = pullRequest.getFromRef().getId();
        this.fromRefDisplayId = pullRequest.getFromRef().getDisplayId();
        this.fromRepositoryId = pullRequest.getFromRef().getRepository().getId();
        this.pullRequestId = pullRequest.getId();
        this.state = pullRequest.getState();
        this.title = pullRequest.getTitle();
        this.toLatestCommit = pullRequest.getToRef().getLatestCommit();
        this.toRefId = pullRequest.getToRef().getId();
        this.toRefDisplayId = pullRequest.getToRef().getDisplayId();
        this.toRepositoryId = pullRequest.getToRef().getRepository().getId();
    }

    @CheckForNull
    public String getDescription() {
        return description;
    }

    public String getFromLatestCommit() {
        return fromLatestCommit;
    }

    public String getFromRefDisplayId() {
        return fromRefDisplayId;
    }

    public String getFromRefId() {
        return fromRefId;
    }

    public int getFromRepositoryId() {
        return fromRepositoryId;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public BitbucketPullRequestState getState() {
        return state;
    }

    public String getTitle() {
        return title;
    }

    public String getToLatestCommit() {
        return toLatestCommit;
    }

    public String getToRefDisplayId() {
        return toRefDisplayId;
    }

    public String getToRefId() {
        return toRefId;
    }

    public int getToRepositoryId() {
        return toRepositoryId;
    }

    public boolean isForkedPullRequest() {
        return fromRepositoryId != toRepositoryId;
    }
}
