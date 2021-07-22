package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeployedToEnvironmentNotifierStepTest {

    private static final String ENV_KEY = "ENV_KEY";
    private static final String ENV_NAME = "ENV_NAME";
    private static final BitbucketDeploymentEnvironmentType ENV_TYPE = BitbucketDeploymentEnvironmentType.PRODUCTION;

    private static final String ENV_URL = "http://my-url";

    @Mock
    private BitbucketDeploymentFactory bitbucketDeploymentFactory;
    @Mock
    private DeploymentPoster deploymentPoster;
    @Mock
    private JenkinsProvider jenkinsProvider;

    @Test
    public void testCreateStepAllowsCustomEnvironmentKey() {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, ENV_URL);
        assertThat(step.getEnvironmentKey(), equalTo(ENV_KEY));
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl().toString(), equalTo(ENV_URL));
    }

    @Test
    public void testCreateStepAllowsNullEnvironmentType() {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, null, ENV_URL);
        assertThat(step.getEnvironmentKey(), equalTo(ENV_KEY));
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), nullValue());
        assertThat(step.getEnvironmentUrl().toString(), equalTo(ENV_URL));
    }

    @Test
    public void testCreateStepAllowsNullEnvironmentUrl() {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, null);
        assertThat(step.getEnvironmentKey(), equalTo(ENV_KEY));
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl(), nullValue());
    }

    @Test
    public void testCreateStepGeneratesEnvironmentKeyWhenBlank() {
        DeployedToEnvironmentNotifierStep step = createStep(" ", ENV_NAME, ENV_TYPE, ENV_URL);
        UUID.fromString(step.getEnvironmentKey()); // This will throw if it's not a UUID
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl().toString(), equalTo(ENV_URL));
    }

    @Test
    public void testCreateStepGeneratesEnvironmentKeyWhenNull() {
        DeployedToEnvironmentNotifierStep step = createStep(null, ENV_NAME, ENV_TYPE, ENV_URL);
        UUID.fromString(step.getEnvironmentKey()); // This will throw if it's not a UUID
        assertThat(step.getEnvironmentName(), equalTo(ENV_NAME));
        assertThat(step.getEnvironmentType(), equalTo(ENV_TYPE));
        assertThat(step.getEnvironmentUrl().toString(), equalTo(ENV_URL));
    }

    @Test
    public void testPerformCallsDeploymentPoster() throws IOException, InterruptedException {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, ENV_URL);
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment(ENV_KEY, ENV_NAME, ENV_TYPE,
                new URL(ENV_URL));

        String serverId = "myServerId";
        BitbucketDeployment deployment = createDeployment(environment);
        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        BitbucketSCMRepository repo = mock(BitbucketSCMRepository.class);
        when(repo.getServerId()).thenReturn(serverId);
        String projectKey = "myProj";
        when(repo.getProjectKey()).thenReturn(projectKey);
        String repoSlug = "myRepo";
        when(repo.getRepositorySlug()).thenReturn(repoSlug);
        when(revisionAction.getBitbucketSCMRepo()).thenReturn(repo);
        String commit = "myCommit";
        when(revisionAction.getRevisionSha1()).thenReturn(commit);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);
        TaskListener listener = mock(TaskListener.class);
        when(bitbucketDeploymentFactory.createDeployment(run, environment)).thenReturn(deployment);

        step.perform(run, null, null, listener);

        verifyZeroInteractions(listener);
        verify(bitbucketDeploymentFactory).createDeployment(run, environment);
        verify(deploymentPoster).postDeployment(serverId, projectKey, repoSlug, commit, deployment, run, listener);
    }

    @Test
    public void testPerformWhenExceptionDoesNotThrow() throws IOException, InterruptedException {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, ENV_URL);
        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment(ENV_KEY, ENV_NAME, ENV_TYPE,
                new URL(ENV_URL));

        Run<?, ?> run = mock(Run.class);
        BitbucketRevisionAction revisionAction = mock(BitbucketRevisionAction.class);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(revisionAction);
        TaskListener listener = mock(TaskListener.class);
        when(bitbucketDeploymentFactory.createDeployment(run, environment))
                .thenThrow(new RuntimeException("Some exception"));

        step.perform(run, null, null, listener);

        verify(listener).error("An error occurred when trying to post the deployment to Bitbucket Server: Some exception");
        verifyNoMoreInteractions(listener);
        verify(bitbucketDeploymentFactory).createDeployment(run, environment);
        verifyZeroInteractions(deploymentPoster);
    }

    @Test
    public void testPerformWhenNoBitbucketRevisionAction() throws IOException, InterruptedException {
        DeployedToEnvironmentNotifierStep step = createStep(ENV_KEY, ENV_NAME, ENV_TYPE, ENV_URL);
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        step.perform(run, null, null, listener);

        verify(listener).error("Could not send deployment notification: DeployedToEnvironmentNotifierStep only works when using the Bitbucket SCM for checkout.");
        verifyNoMoreInteractions(listener);
        verifyZeroInteractions(bitbucketDeploymentFactory);
        verifyZeroInteractions(deploymentPoster);
    }

    private BitbucketDeployment createDeployment(BitbucketDeploymentEnvironment environment) {
        return new BitbucketDeployment(1, "desc", "name", environment, "key", DeploymentState.FAILED, "url");
    }

    private DeployedToEnvironmentNotifierStep createStep(String environmentKey, String environmentName,
                                                         @CheckForNull BitbucketDeploymentEnvironmentType environmentType,
                                                         @CheckForNull String environmentUrl) {
        String enumString = environmentType == null ? null : environmentType.toString();
        return new DeployedToEnvironmentNotifierStep(environmentKey, environmentName, enumString, environmentUrl) {
            @Override
            public DescriptorImpl descriptor() {
                DescriptorImpl descriptor = mock(DescriptorImpl.class);
                when(descriptor.getBitbucketDeploymentFactory()).thenReturn(bitbucketDeploymentFactory);
                when(descriptor.getDeploymentPoster()).thenReturn(deploymentPoster);
                return descriptor;
            }
        };
    }
}