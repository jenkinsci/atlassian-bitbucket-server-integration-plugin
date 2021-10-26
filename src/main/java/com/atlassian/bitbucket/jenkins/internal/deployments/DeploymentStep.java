package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.CheckForNull;

/**
 * @since 3.1.0
 */
public interface DeploymentStep {

    /**
     * Generates a {@link BitbucketDeploymentEnvironment} from the various components.
     *
     * @param run      the run that is performing the deployments
     * @param listener the {@link TaskListener}
     * @return the configured {@link BitbucketDeploymentEnvironment}
     */
    BitbucketDeploymentEnvironment getEnvironment(Run<?, ?> run, TaskListener listener);

    /**
     * Used to populate the {@code environmentKey} field in the UI.
     *
     * @return the configured or generated environment key
     */
    String getEnvironmentKey();

    /**
     * Used to populate the {@code environmentName} field in the UI.
     *
     * @return the configured environment name or {@code null} if not configured
     */
    @CheckForNull
    String getEnvironmentName();

    /**
     * Used to populate the {@code environmentType} field in the UI.
     *
     * @return the configured {@link BitbucketDeploymentEnvironmentType#name()} or {@code null} if not configured
     */
    @CheckForNull
    String getEnvironmentType();

    /**
     * Used to populate the {@code environmentUrl} field in the UI.
     *
     * @return the configured environment url or {@code null} if not configured
     */
    @CheckForNull
    String getEnvironmentUrl();
}
