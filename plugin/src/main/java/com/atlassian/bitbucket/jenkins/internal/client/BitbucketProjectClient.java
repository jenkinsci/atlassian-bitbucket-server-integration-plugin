package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;

/**
 * Client to get a project from the Bitbucket server.
 */
public interface BitbucketProjectClient {

    /**
     * Get the project associated with the given project key
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
    BitbucketProject getProject();

    /**
     * Return a repository search client
     *
     * @param repositorySlug the repository slug
     * @return a client that is ready to use
     */
    BitbucketRepositoryClient getRepositoryClient(String repositorySlug);
}
