package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMCheckoutListener;
import com.google.inject.ImplementedBy;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Send a deployment notification to Bitbucket Server
 *
 * @since deployments
 */
@ImplementedBy(DeploymentPosterImpl.class)
public interface DeploymentPoster extends BitbucketSCMCheckoutListener {

    /**
     * Send a notification of deployment to Bitbucket Server on the provided commit.
     *
     * @param revisionAction the {@link BitbucketRevisionAction revision information}
     * @param deployment     the deployment information
     * @param run            the run that caused the deployment (used to get the credentials to post the notification)
     * @param taskListener   the task listener for the run, in order to write messages to the run's console
     */
    void postDeployment(BitbucketRevisionAction revisionAction, BitbucketDeployment deployment, Run<?, ?> run,
                        TaskListener taskListener);
}
