package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketBuildStatusClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BuildStatusPosterTest {

    private static final String REVISION_SHA1 = "67d71c2133aab0e070fb8100e3e71220332c5af1";
    private static final String SERVER_ID = "FakeServerId";
    private static final String SERVER_URL = "http://www.example.com";

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Captor
    private ArgumentCaptor<BitbucketBuildStatus> captor;
    @Mock
    private BitbucketRevisionAction action;
    @Mock
    private AbstractBuild build;
    @Mock
    private BitbucketClientFactory factory;
    @Mock
    private BitbucketClientFactoryProvider factoryProvider;
    @Mock
    private TaskListener listener;
    @Mock
    private PrintStream logger;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private BitbucketBuildStatusClient postClient;
    @Mock
    private AbstractProject project;
    @Mock
    private BitbucketServerConfiguration server;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @InjectMocks
    private BuildStatusPoster buildStatusPoster;

    @Before
    public void setup() {
        when(build.isBuilding()).thenReturn(true);
        when(build.getId()).thenReturn("10");
        when(build.getDurationString()).thenReturn("23 sec");
        when(build.getProject()).thenReturn(project);
        when(build.getUrl()).thenReturn("job%2FTest%2520Project%2F14%2F");
        when(action.getRevisionSha1()).thenReturn(REVISION_SHA1);
        when(action.getServerId()).thenReturn(SERVER_ID);
        when(listener.getLogger()).thenReturn(logger);
        when(server.getBaseUrl()).thenReturn(SERVER_URL);
        when(factoryProvider.getClient(eq(SERVER_URL), any(BitbucketCredentials.class)))
                .thenReturn(factory);
        when(factory.getBuildStatusClient(REVISION_SHA1)).thenReturn(postClient);
        BitbucketCredentials credentials = mock(BitbucketCredentials.class);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(any(Credentials.class),
                any(BitbucketServerConfiguration.class))).thenReturn(credentials);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials((Credentials) isNull(),
                any(BitbucketServerConfiguration.class))).thenReturn(credentials);
    }

    @Test
    public void testBitbucketClientException() {
        when(build.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(server));
        doThrow(BitbucketClientException.class).when(postClient).post(any(BitbucketBuildStatus.class));
        buildStatusPoster.postBuildStatus(build, listener);
        verify(postClient).post(any());
    }

    @Test
    public void testNoBuildAction() {
        when(build.getAction(BitbucketRevisionAction.class)).thenReturn(null);
        buildStatusPoster.postBuildStatus(build, listener);
        verifyZeroInteractions(pluginConfiguration);
        verifyZeroInteractions(listener);
    }

    @Test
    public void testNoMatchingServer() {
        when(build.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.empty());
        buildStatusPoster.postBuildStatus(build, listener);
        verify(listener).error(eq("Failed to post build status as the provided Bitbucket Server config does not exist"));
        verifyZeroInteractions(factoryProvider);
    }

    @Test
    public void testSuccessfulPost() {
        when(build.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(server));

        buildStatusPoster.postBuildStatus(build, listener);
        verify(postClient).post(captor.capture());
        BitbucketBuildStatus status = captor.getValue();
        assertThat(status.getKey(), equalTo(build.getId()));
    }
}