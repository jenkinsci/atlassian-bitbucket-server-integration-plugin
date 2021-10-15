package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentStepExecutionCallbackTest {

    private static final String REVISION_SHA = "revisionSha";

    private DeploymentStepExecutionCallback callback;
    @Mock
    private BitbucketDeploymentEnvironment environment;
    @Mock
    private StepContext context;
    @Mock
    private BitbucketDeploymentFactory deploymentFactory;
    @Mock
    private DeploymentPoster deploymentPoster;
    @Mock
    private TaskListener taskListener;
    @Mock
    private Run<?, ?> run;
    @Mock
    private BitbucketRevisionAction bitbucketRevisionAction;
    @Mock
    private BitbucketSCMRepository repo;

    @Before
    public void setup() throws Exception {
        callback = new DeploymentStepExecutionCallback(environment) {
            @Override
            DeploymentStepImpl.DescriptorImpl getStepDescriptor() {
                DeploymentStepImpl.DescriptorImpl descriptor = mock(DeploymentStepImpl.DescriptorImpl.class);
                when(descriptor.getBitbucketDeploymentFactory()).thenReturn(deploymentFactory);
                when(descriptor.getDeploymentPoster()).thenReturn(deploymentPoster);
                return descriptor;
            }
        };
        when(context.get(TaskListener.class)).thenReturn(taskListener);
        when(context.get(Run.class)).thenReturn(run);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(bitbucketRevisionAction);
        when(bitbucketRevisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        when(bitbucketRevisionAction.getRevisionSha1()).thenReturn(REVISION_SHA);
    }

    @Test
    public void testCallbackWhenNoTaskListener() throws Exception {
        when(context.get(TaskListener.class)).thenReturn(null);

        callback.onStart(context);

        verify(context).get(TaskListener.class);
        verifyZeroInteractions(deploymentFactory);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testCallbackWhenNoRun() throws Exception {
        when(context.get(Run.class)).thenReturn(null);

        callback.onStart(context);

        verify(context).get(TaskListener.class);
        verify(context).get(Run.class);
        verifyZeroInteractions(deploymentFactory);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testCallbackWhenNoBitbucketRevisionAction() throws Exception {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(null);

        callback.onStart(context);

        verify(context).get(TaskListener.class);
        verify(context).get(Run.class);
        verify(run).getAction(BitbucketRevisionAction.class);
        verifyZeroInteractions(deploymentFactory);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testCallbackContinuesWhenException() throws Exception {
        RuntimeException e = new RuntimeException("An unexpected error!");
        when(context.get(TaskListener.class)).thenThrow(e);
        Object result = new Object();

        callback.onSuccess(context, result);

        verifyZeroInteractions(deploymentFactory);
        verifyZeroInteractions(deploymentPoster);
        verify(context).onSuccess(result);
    }

    @Test
    public void testCallbackOnStart() {
        testCallback(() -> callback.onStart(context), DeploymentState.IN_PROGRESS);
        verify(context, times(0)).onSuccess(any());
        verify(context, times(0)).onFailure(any());
    }

    @Test
    public void testCallbackOnSuccess() {
        Object result = "";
        testCallback(() -> callback.onSuccess(context, result), DeploymentState.SUCCESSFUL);
        verify(context).onSuccess(result);
        verify(context, times(0)).onFailure(any());
    }

    @Test
    public void testCallbackOnFailure() {
        RuntimeException t = new RuntimeException();
        testCallback(() -> callback.onFailure(context, t), DeploymentState.FAILED);
        verify(context, times(0)).onSuccess(any());
        verify(context).onFailure(t);
    }

    private void testCallback(Runnable callbackMethod, DeploymentState state) {
        BitbucketDeployment deployment = mock(BitbucketDeployment.class);
        when(deploymentFactory.createDeployment(run, environment, state)).thenReturn(deployment);

        callbackMethod.run();

        verify(deploymentPoster).postDeployment(repo, REVISION_SHA, deployment, run, taskListener);
    }
}