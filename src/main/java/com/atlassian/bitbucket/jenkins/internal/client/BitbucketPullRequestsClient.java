package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;

import java.util.stream.Stream;

/**
 * A client to get the state of pull requests in Bitbucket Server.
 */
public interface BitbucketPullRequestsClient {

    /**
     * Gets all the open pull requests for the repository. The returned stream will make paged calls to Bitbucket to
     * ensure that all pull requests are returned. Consumers are advised that this can return large amounts of data
     * and are <strong>strongly</strong> encouraged to not collect to a list or similar before processing items, but
     * rather process them as they come in.
     *
     * @return a stream of all (potentially spanning multiple pages) open pull requests
     */
    Stream<BitbucketPullRequest> getOpenPullRequests();
}
