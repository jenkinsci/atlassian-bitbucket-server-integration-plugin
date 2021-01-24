package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

import java.util.stream.Stream;

/**
 * Repository client, used to interact with a remote repository for all operations except cloning
 * source code.
 */
public interface BitbucketRepositoryClient {

    /**
     * Make the call out to Bitbucket and read the response.
     *
     * @return the result of the call
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    BitbucketRepository getRepository();

    /**
     * A client for performing various webhook related operations.
     *
     * @return a client.
     */
    BitbucketWebhookClient getWebhookClient();

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
