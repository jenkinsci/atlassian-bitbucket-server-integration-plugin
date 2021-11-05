package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.logging.Logger;

import static java.lang.String.format;

public class DeploymentStepExecutionCallback extends BodyExecutionCallback {

    private static final Logger LOGGER = Logger.getLogger(DeploymentStepExecutionCallback.class.getName());
    private static final long serialVersionUID = 1L;

    private final BitbucketDeploymentEnvironment environment;

    public DeploymentStepExecutionCallback(BitbucketDeploymentEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void onStart(StepContext context) {
        sendNotification(context, DeploymentState.IN_PROGRESS);
    }

    @Override
    public void onSuccess(StepContext context, Object result) {
        sendNotification(context, DeploymentState.SUCCESSFUL);
        context.onSuccess(result);
    }

    @Override
    public void onFailure(StepContext context, Throwable t) {
        sendNotification(context, DeploymentState.FAILED);
        context.onFailure(t);
    }

    BitbucketDeploymentEnvironment getEnvironment() {
        return environment;
    }

    DeploymentStepImpl.DescriptorImpl getStepDescriptor() {
        DeploymentStepImpl.DescriptorImpl stepDescriptor = Jenkins.get().getDescriptorByType(DeploymentStepImpl.DescriptorImpl.class);
        if (stepDescriptor == null) {
            throw new IllegalStateException("Cannot get descriptor for DeploymentStepImpl.DescriptorImpl");
        }
        return stepDescriptor;
    }

    private void sendNotification(StepContext context, DeploymentState state) {
        TaskListener listener;
        try {
            listener = context.get(TaskListener.class);
        } catch (Exception e) {
            LOGGER.warning(getErrorMessage(e.getMessage()));
            return;
        }
        if (listener == null) {
            LOGGER.warning(getErrorMessage("No TaskListener in the StepContext"));
            return;
        }
        try {
            Run<?, ?> run = context.get(Run.class);
            if (run == null) {
                LOGGER.warning(getErrorMessage("No Run in the StepContext"));
                return;
            }
            BitbucketRevisionAction revisionAction = run.getAction(BitbucketRevisionAction.class);
            if (revisionAction == null) {
                // Not checked out with a Bitbucket SCM
                listener.error(getErrorMessage("The Run is not using Bitbucket SCM for checkout"));
                return;
            }

            BitbucketDeployment deployment = getStepDescriptor().getBitbucketDeploymentFactory()
                    .createDeployment(run, environment, state);
            BitbucketSCMRepository bitbucketSCMRepo = revisionAction.getBitbucketSCMRepo();
            String revisionSha = revisionAction.getRevisionSha1();

            getStepDescriptor().getDeploymentPoster().postDeployment(bitbucketSCMRepo, revisionSha, deployment, run, listener);
        } catch (Exception e) {
            listener.error(getErrorMessage(e.getMessage()));
        }
    }

    private String getErrorMessage(String e) {
        return format("There was an error sending the deployment information to Bitbucket Server: %s", e);
    }

}
