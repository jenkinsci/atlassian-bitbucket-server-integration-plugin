package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;

/**
 * Client for interacting with Bitbucket Server's deployments API.
 *
 * @since 3.1.0
 */
public interface BitbucketDeploymentClient {

    /**
     * Send notification of a deployment to Bitbucket Server.
     *
     * @param deployment the deployment to send
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    void post(BitbucketDeployment deployment);
}
