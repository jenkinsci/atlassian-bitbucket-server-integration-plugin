package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentStepExecutionTest {

    @Mock
    private BodyInvoker bodyInvoker;
    @Mock
    private BitbucketDeploymentEnvironment environment;
    @InjectMocks
    private DeploymentStepExecution deploymentStepExecution;
    @Mock
    private StepContext context;

    @Before
    public void setup() throws Exception {
        when(context.newBodyInvoker()).thenReturn(bodyInvoker);
        when(bodyInvoker.withCallback(ArgumentMatchers.any(DeploymentStepExecutionCallback.class))).thenReturn(bodyInvoker);
    }

    @Test
    public void testDeploymentStepExecutionStart() throws Exception {
        assertFalse(deploymentStepExecution.start());

        ArgumentCaptor<DeploymentStepExecutionCallback> captor = ArgumentCaptor.forClass(DeploymentStepExecutionCallback.class);
        verify(bodyInvoker).withCallback(captor.capture());
        DeploymentStepExecutionCallback callback = captor.getValue();
        assertThat(callback.getEnvironment(), equalTo(environment));
        verify(bodyInvoker).start();
    }
}