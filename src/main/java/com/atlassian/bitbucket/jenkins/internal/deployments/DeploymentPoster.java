package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.google.inject.ImplementedBy;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Send a deployment notification to Bitbucket Server
 *
 * @since deployments
 */
@ImplementedBy(DeploymentPosterImpl.class)
public interface DeploymentPoster {

    /**
     * Send a notification of deployment to Bitbucket Server on the provided commit.
     *
     * @param bbsServerId    the ID of the Bitbucket Server instance to send the notification to
     * @param projectKey     the key of the project that was deployed
     * @param repositorySlug the slug of the repository that was deployed
     * @param revisionSha    the commit that was deployed
     * @param deployment     the deployment information
     * @param run            the run that caused the deployment (used to get the credentials to post the notification)
     * @param taskListener   the task listener for the run, in order to write messages to the run's console
     */
    void postDeployment(String bbsServerId, String projectKey, String repositorySlug, String revisionSha,
                        BitbucketDeployment deployment, Run<?, ?> run, TaskListener taskListener);
}
