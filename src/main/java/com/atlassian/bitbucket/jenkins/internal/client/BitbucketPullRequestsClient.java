package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;

import java.util.stream.Stream;

/**
 * A client to get the state of pull requests in Bitbucket Server.
 */
public interface BitbucketPullRequestsClient {

    Stream<BitbucketPullRequest> getOpenPullRequests();
}
