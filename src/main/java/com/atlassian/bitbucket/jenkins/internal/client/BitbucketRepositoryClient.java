package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

import java.util.stream.Stream;

/**
 * Repository client, used to interact with a remote repository for all operations except cloning
 * source code.
 */
public interface BitbucketRepositoryClient {

    /**
     * Returns a client for getting file content and directory information on paths in a repository.
     *
     * @return A client that is ready to use
     * @since 3.0.0
     */
    BitbucketFilePathClient getFilePathClient();

    /**
     * Make the call out to Bitbucket and read the response.
     *
     * @return the result of the call
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws NoContentException         if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    BitbucketRepository getRepository();

    /**
     * Returns a client for performing various webhook related operations.
     *
     * @return a client that is ready to use
     */
    BitbucketWebhookClient getWebhookClient();

    /**
     * Gets all pull requests of the given state for the repository. The returned stream will make paged calls to
     * Bitbucket to ensure that all pull requests are returned. Consumers are advised that this can return large amounts
     * of data and are <strong>strongly</strong> encouraged to not collect to a list or similar before processing items,
     * but rather process them as they come in.
     *
     * @param state the state of the pull requests to fetch
     * @return a stream of all pull requests in the repository with the given state
     * @since 3.0.0
     */
    Stream<BitbucketPullRequest> getPullRequests(BitbucketPullRequestState state);

    /**
     * Gets all pull requests for the repository. The returned stream will make paged calls to Bitbucket to
     * ensure that all pull requests are returned. Consumers are advised that this can return large amounts of data
     * and are <strong>strongly</strong> encouraged to not collect to a list or similar before processing items, but
     * rather process them as they come in.
     *
     * @return a stream of all pull requests in the repository
     * @since 3.0.0
     */
    Stream<BitbucketPullRequest> getPullRequests();
}
