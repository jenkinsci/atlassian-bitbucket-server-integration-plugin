package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;

import java.util.Optional;

/**
 * Client to retrieve the username for the credentials used.
 */
public interface BitbucketAuthenticatedUserClient {

    /**
     * Get the username associated with the provided credentials.
     *
     * @return The associated username, or {@link Optional#empty} if there is none
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    Optional<String> getAuthenticatedUser();
}
