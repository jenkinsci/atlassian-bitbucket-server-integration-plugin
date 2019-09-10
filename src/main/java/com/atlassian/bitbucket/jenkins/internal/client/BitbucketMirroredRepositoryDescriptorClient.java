package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

/**
 * Client to get the mirrored repository descriptor for a given repository.
 */
public interface BitbucketMirroredRepositoryDescriptorClient {

    /**
     * Retrieve a page of mirrored repository descriptors for the given repository ID
     *
     * @return The mirrored repository descriptors for the given repository
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    BitbucketPage<BitbucketMirroredRepositoryDescriptor> get(int repositoryId);
}
