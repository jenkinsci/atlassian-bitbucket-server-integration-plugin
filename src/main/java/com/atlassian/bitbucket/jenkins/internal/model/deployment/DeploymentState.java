package com.atlassian.bitbucket.jenkins.internal.model.deployment;

/**
 * The possible states of a deployment.
 *
 * @since deployments
 */
public enum DeploymentState {

    /**
     * The deployment has been scheduled, but has not started.
     */
    PENDING("%s is waiting to deploy to %s."),
    /**
     * The deployment is currently in progress.
     */
    IN_PROGRESS("%s is deploying to %s."),
    /**
     * The deployment started, but was stopped part way through.
     */
    CANCELLED("%s deployment to %s cancelled."),
    /**
     * The deployment failed to complete.
     */
    FAILED("%s failed to deploy to %s."),
    /**
     * The commit is no longer deployed to the the environment.
     */
    ROLLED_BACK("%s deployment to %s was rolled back."),
    /**
     * The deployment was successful.
     */
    SUCCESSFUL("%s successfully deployed to %s"),
    /**
     * The state of the deployment is not known.
     */
    UNKNOWN("State of %s deploying to %s is unknown.");

    private final String formatString;

    DeploymentState(String formatString) {
        this.formatString = formatString;
    }

    public String getDescriptiveTest(String jobName, String environmentName) {
        return String.format(formatString, jobName, environmentName);
    }
}
