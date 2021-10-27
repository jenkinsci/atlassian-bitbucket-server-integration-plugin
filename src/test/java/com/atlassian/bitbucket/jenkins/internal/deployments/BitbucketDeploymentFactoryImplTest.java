package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketDeploymentFactoryImplTest {

    private static final String RUN_URL = "run-url";

    @InjectMocks
    BitbucketDeploymentFactoryImpl deploymentFactory;
    @Mock
    DisplayURLProvider displayURLProvider;
    @Mock
    Jenkins jenkins;

    @Before
    public void setup() {
        when(displayURLProvider.getRunURL(any())).thenReturn(RUN_URL);
        when(jenkins.getFullName()).thenReturn("");
    }

    @Test
    public void testCreateDeploymentAborted() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name deployment to My-env cancelled.",
                "my-display-name", environment, "my-key", DeploymentState.CANCELLED, RUN_URL);
        Result result = Result.ABORTED;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentFailed() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name failed to deploy to My-env.",
                "my-display-name", environment, "my-key", DeploymentState.FAILED, RUN_URL);
        Result result = Result.FAILURE;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentInProgress() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name is deploying to My-env.",
                "my-display-name", environment, "my-key", DeploymentState.IN_PROGRESS, RUN_URL);
        Result result = null;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentNotBuilt() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name deployment to My-env cancelled.",
                "my-display-name", environment, "my-key", DeploymentState.CANCELLED, RUN_URL);
        Result result = Result.NOT_BUILT;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentPending() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name is waiting to deploy to My-env.",
                "my-display-name", environment, "my-key", DeploymentState.PENDING, RUN_URL);
        Result result = null;
        FreeStyleBuild run = mockRun(expected, result, true);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentSuccessful() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my display name successfully deployed to My-env",
                "my display name", environment, "my-key", DeploymentState.SUCCESSFUL, RUN_URL);
        Result result = Result.SUCCESS;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentUnstable() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name successfully deployed to My-env",
                "my-display-name", environment, "my-key", DeploymentState.SUCCESSFUL, RUN_URL);
        Result result = Result.UNSTABLE;
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testCreateDeploymentWithSpecificState() {
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env");
        BitbucketDeployment expected = new BitbucketDeployment(42, "my-display-name is deploying to My-env.",
                "my-display-name", environment, "my-key", DeploymentState.IN_PROGRESS, RUN_URL);
        Result result = Result.SUCCESS; // Something different to expected to make sure we're using that instead
        FreeStyleBuild run = mockRun(expected, result, false);

        BitbucketDeployment actual = deploymentFactory.createDeployment(run, environment,
                DeploymentState.IN_PROGRESS);

        assertThat(actual, equalTo(expected));
    }

    private FreeStyleBuild mockRun(BitbucketDeployment expected, Result result, boolean hasntStartedYet) {
        FreeStyleBuild run = mock(FreeStyleBuild.class);
        FreeStyleProject job = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(job);
        when(job.getParent()).thenReturn(jenkins);
        when(run.getNumber()).thenReturn((int) expected.getDeploymentSequenceNumber());
        when(run.getResult()).thenReturn(result);
        when(job.getDisplayName()).thenReturn(expected.getDisplayName());
        when(job.getName()).thenReturn(expected.getKey());
        when(run.hasntStartedYet()).thenReturn(hasntStartedYet);
        return run;
    }
}