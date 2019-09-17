package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;

/**
 * Client to get capabilities from the remote server.
 */
public interface BitbucketCapabilitiesClient {

    /**
     * Get the server capabilities
     *
     * @return the capabilities of the linked Bitbucket Server
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    AtlassianServerCapabilities getServerCapabilities();

    /**
     * Make the supported webhook events for the linked Bitbucket Server.
     *
     * @return the supported events
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    BitbucketWebhookSupportedEvents getWebhookSupportedEvents() throws BitbucketMissingCapabilityException;
}
