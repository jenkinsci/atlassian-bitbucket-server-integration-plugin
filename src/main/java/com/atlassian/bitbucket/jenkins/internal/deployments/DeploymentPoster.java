package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.google.inject.ImplementedBy;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Send a deployment notification to Bitbucket Server
 *
 * @since 3.1.0
 */
@ImplementedBy(DeploymentPosterImpl.class)
public interface DeploymentPoster {

    /**
     * Send a notification of deployment to Bitbucket Server on the provided commit.
     *
     * @param repository     the repository that was deployed
     * @param revisionSha    the commit that was deployed
     * @param deployment     the deployment information
     * @param run            the run that caused the deployment (used to get the credentials to post the notification)
     * @param taskListener   the task listener for the run, in order to write messages to the run's console
     */
    void postDeployment(BitbucketSCMRepository repository, String revisionSha,
                        BitbucketDeployment deployment, Run<?, ?> run, TaskListener taskListener);
}
