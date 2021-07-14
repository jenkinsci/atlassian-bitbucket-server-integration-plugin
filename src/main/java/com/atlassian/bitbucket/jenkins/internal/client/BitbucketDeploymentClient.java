package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;

/**
 * Client for interacting with Bitbucket Server's deployments API.
 *
 * @since deployments
 */
public interface BitbucketDeploymentClient {

    /**
     * Send notification of the deployment to Bitbucket Server
     *
     * @param deployment the deployment to send
     */
    void post(BitbucketDeployment deployment);
}
