package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class DeploymentStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;

    private final BitbucketDeploymentEnvironment environment;

    DeploymentStepExecution(BitbucketDeploymentEnvironment environment,
                            StepContext context) {
        super(context);
        this.environment = environment;
    }

    @Override
    public boolean start() throws Exception {
        getContext().newBodyInvoker()
                .withCallback(new DeploymentStepExecutionCallback(environment))
                .start();
        return false;
    }

    BitbucketDeploymentEnvironment getEnvironment() {
        return environment;
    }
}
