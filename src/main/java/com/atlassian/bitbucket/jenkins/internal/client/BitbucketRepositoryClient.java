package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

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
}
