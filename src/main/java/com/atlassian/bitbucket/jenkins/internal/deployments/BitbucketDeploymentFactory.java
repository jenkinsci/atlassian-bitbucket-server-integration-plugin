package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.google.inject.ImplementedBy;
import hudson.model.Run;

import javax.annotation.CheckForNull;

/**
 * A factory for creating instances of {@link BitbucketDeployment} from job information.
 *
 * @since 3.1.0
 */
@ImplementedBy(BitbucketDeploymentFactoryImpl.class)
public interface BitbucketDeploymentFactory {

    /**
     * Create a deployment from the provided {@link Run} and {@link BitbucketDeploymentEnvironment}.
     *
     * The value of {@link BitbucketDeployment#getState()} will be populated based on the {@link Run#getResult()}.
     *
     * @param run the run that deployed to the environment
     * @param environment the environment that was deployed to
     * @return a {@link BitbucketDeployment} matching the provided information
     */
    BitbucketDeployment createDeployment(Run<?, ?> run,
                                         BitbucketDeploymentEnvironment environment);

    /**
     * Create a deployment from the provided {@link Run}, {@link BitbucketDeploymentEnvironment} and
     * {@link DeploymentState}.
     *
     * The value of {@link Run#getResult()} will be ignored and the {@link BitbucketDeployment#getState()} will be
     * populated based on the provided {@code state}
     *
     * @param run the run that deployed to the environment
     * @param environment the environment that was deployed to
     * @param state the result of the deployment
     * @return a {@link BitbucketDeployment} matching the provided information
     */
    BitbucketDeployment createDeployment(Run<?, ?> run,
                                         BitbucketDeploymentEnvironment environment,
                                         @CheckForNull DeploymentState state);
}
