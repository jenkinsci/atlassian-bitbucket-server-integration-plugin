package com.atlassian.bitbucket.jenkins.internal.model.deployment;

/**
 * The possible states of a deployment.
 *
 * @since deployments
 */
public enum DeploymentState {

    PENDING,
    IN_PROGRESS,
    CANCELLED,
    FAILED,
    ROLLED_BACK,
    SUCCESSFUL,
    UNKNOWN
}
