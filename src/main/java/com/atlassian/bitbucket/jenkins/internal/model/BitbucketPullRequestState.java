package com.atlassian.bitbucket.jenkins.internal.model;

/**
 * The state a PullRequest can be in.
 * @since 3.0.0
 */
public enum BitbucketPullRequestState {
    DECLINED,
    DELETED,
    MERGED,
    OPEN
}
