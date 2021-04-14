package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

import java.util.List;

/**
 * Repository client, used to interact with a remote repository for all operations except cloning
 * source code.
 */
public interface BitbucketRepositoryClient {

    /**
     * Makes a call out to this repository to return a list of all branches
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
    List<BitbucketBranch> getBranches();

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
     * A client for getting file content and directory information on paths in a repository
     *
     * @return a client.
     */
    BitbucketFilePathClient getFilePathClient();
}
