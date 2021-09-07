package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketDeploymentClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketCDCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;

import static java.lang.String.format;
import static java.util.Collections.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentPosterImplTest {

    private static final String BASE_URL = "http://localhost:7990/bitbucket";
    private static final BitbucketDeploymentEnvironment ENVIRONMENT = new BitbucketDeploymentEnvironment("ENV-KEY", "Env name");
    private static final BitbucketDeployment DEPLOYMENT = new BitbucketDeployment(1, "desc", "name",
            ENVIRONMENT, "key", DeploymentState.FAILED, "url");
    private static final String PROJECT_KEY = "projectKey";
    private static final String REPO_SLUG = "repoSlug";
    private static final String REVISION_SHA = "revisionSha";
    private static final String SERVER_ID = "bbsServerId";
    private static final String SERVER_NAME = "localhost";

    @Mock
    public BitbucketClientFactoryProvider clientFactoryProvider;

    @InjectMocks
    public DeploymentPosterImpl poster;
    @Mock
    public Run<FreeStyleProject, ?> run;
    @Mock
    public TaskListener taskListener;
    @Mock
    private BitbucketCredentials bitbucketCredentials;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private BitbucketClientFactory clientFactory;
    @Mock
    private BitbucketTokenCredentials globalAdminCredentials;
    @Mock
    private GlobalCredentialsProvider globalCredentialsProvider;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private PrintStream printStream;
    @Mock
    private BitbucketServerConfiguration server;

    @Before
    public void setup() {
        FreeStyleProject parent = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(parent);
        when(globalCredentialsProvider.getGlobalAdminCredentials()).thenReturn(of(globalAdminCredentials));
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(of(server));
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(globalAdminCredentials)).thenReturn(bitbucketCredentials);
        when(server.getGlobalCredentialsProvider(any(Item.class))).thenReturn(globalCredentialsProvider);
        when(server.getBaseUrl()).thenReturn(BASE_URL);
        when(clientFactoryProvider.getClient(BASE_URL, bitbucketCredentials)).thenReturn(clientFactory);
        when(clientFactory.getCapabilityClient().getCDCapabilities())
                .thenReturn(new BitbucketCDCapabilities(emptyMap()));
        when(server.getServerName()).thenReturn(SERVER_NAME);
        when(taskListener.getLogger()).thenReturn(printStream);
    }

    @Test
    public void testPostDeployment() {
        poster.postDeployment(SERVER_ID, PROJECT_KEY, REPO_SLUG, REVISION_SHA, DEPLOYMENT, run, taskListener);

        verify(printStream).println(format("Sending notification of %s to %s on commit %s",
                DEPLOYMENT.getState().name(), SERVER_NAME, REVISION_SHA));
        verify(clientFactory.getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getDeploymentClient(REVISION_SHA))
                .post(DEPLOYMENT);
        verify(printStream).println(format("Sent notification of %s deployment to %s on commit %s",
                DEPLOYMENT.getState().name(), SERVER_NAME, REVISION_SHA));
    }

    @Test
    public void testPostDeploymentAuthorizationException() {
        BitbucketDeploymentClient deploymentClient = clientFactory.getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getDeploymentClient(REVISION_SHA);
        doThrow(new AuthorizationException("An auth error", 400, "")).when(deploymentClient).post(DEPLOYMENT);
        poster.postDeployment(SERVER_ID, PROJECT_KEY, REPO_SLUG, REVISION_SHA, DEPLOYMENT, run, taskListener);

        verify(printStream).println(format("Sending notification of %s to %s on commit %s",
                DEPLOYMENT.getState().name(), SERVER_NAME, REVISION_SHA));
        verify(taskListener).error(format("The personal access token for the Bitbucket Server instance %s is invalid or insufficient to post deployment information: %s",
                SERVER_NAME, "An auth error"));
    }

    @Test
    public void testPostDeploymentClientException() {
        BitbucketDeploymentClient deploymentClient = clientFactory.getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getDeploymentClient(REVISION_SHA);
        doThrow(new BitbucketClientException("A Bitbucket error", 500, "")).when(deploymentClient).post(DEPLOYMENT);
        poster.postDeployment(SERVER_ID, PROJECT_KEY, REPO_SLUG, REVISION_SHA, DEPLOYMENT, run, taskListener);

        verify(printStream).println(format("Sending notification of %s to %s on commit %s",
                DEPLOYMENT.getState().name(), SERVER_NAME, REVISION_SHA));
        verify(taskListener).error(format("Failed to send notification of deployment to %s due to an error: %s",
                server.getServerName(), "A Bitbucket error"));
    }

    @Test
    public void testPostDeploymentWithDeploymentsNotSupported() {
        when(clientFactory.getCapabilityClient().getCDCapabilities())
                .thenReturn(null);

        poster.postDeployment(SERVER_ID, PROJECT_KEY, REPO_SLUG, REVISION_SHA, DEPLOYMENT, run, taskListener);

        verify(taskListener).error(
                format("Could not send deployment notification to %s: The Bitbucket version does not support deployments", SERVER_NAME));
        verify(clientFactory, never()).getProjectClient(any());
    }

    @Test
    public void testPostDeploymentWithUnknownServerId() {
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(empty());

        poster.postDeployment(SERVER_ID, PROJECT_KEY, REPO_SLUG, REVISION_SHA, DEPLOYMENT, run, taskListener);

        verify(pluginConfiguration).getServerById(SERVER_ID);
        verify(taskListener).error(
                "Could not send deployment notification to Bitbucket Server: Unknown serverId " + SERVER_ID);
        verifyZeroInteractions(clientFactoryProvider);
        verifyZeroInteractions(jenkinsToBitbucketCredentials);
    }
}