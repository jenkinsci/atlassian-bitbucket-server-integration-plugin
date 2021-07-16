package com.atlassian.bitbucket.jenkins.internal.model.deployment;

/**
 * The types of environments available via the Bitbucket Server API.
 *
 * @since deployments
 */
public enum BitbucketDeploymentEnvironmentType {

    DEVELOPMENT,
    TESTING,
    STAGING,
    PRODUCTION;
}
