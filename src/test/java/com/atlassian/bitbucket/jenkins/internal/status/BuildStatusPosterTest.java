package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.fixture.mocks.BitbucketJenkinsSetup;
import com.atlassian.bitbucket.jenkins.internal.fixture.mocks.TestBitbucketClientFactoryHandler;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.fixture.mocks.BitbucketJenkinsSetup.SERVER_ID;
import static com.atlassian.bitbucket.jenkins.internal.model.BuildState.SUCCESSFUL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(Silent.class)
public class BuildStatusPosterTest {

    private static final String PROJECT_NAME = "Project Name";
    private static final String REPO_SLUG = "repo";
    private static final String REVISION_SHA1 = "67d71c2133aab0e070fb8100e3e71220332c5af1";
    private static final String SERVER_URL = "http://www.example.com";
    private static final BitbucketSCMRepository scmRepository =
            new BitbucketSCMRepository(null, null, PROJECT_NAME, PROJECT_NAME, REPO_SLUG, REPO_SLUG, SERVER_ID, "");
    private static final BitbucketRevisionAction action =
            new BitbucketRevisionAction(scmRepository, "master", REVISION_SHA1);

    @Mock
    private AbstractBuild run;
    @Mock
    private TaskListener listener;
    @Mock
    private PrintStream logger;
    @Mock
    private AbstractProject project;
    @Mock
    private BitbucketBuildStatusFactory buildStatusFactory;

    private BitbucketBuildStatus.Builder buildStatus = new BitbucketBuildStatus.Builder("key", SUCCESSFUL, "aUrl");
    private TestBitbucketClientFactoryHandler clientFactoryMock;
    private BitbucketJenkinsSetup jenkinsSetupMock;
    private BuildStatusPoster buildStatusPoster;

    @Before
    public void setup() {
        jenkinsSetupMock = BitbucketJenkinsSetup.create().assignGlobalCredentialProviderToItem(project);
        clientFactoryMock =
                TestBitbucketClientFactoryHandler.create(jenkinsSetupMock, jenkinsSetupMock.getBbAdminCredentials())
                        .withBuildStatusClient(REVISION_SHA1, scmRepository)
                        .withCICapabilities("richBuildStatus");

        buildStatusPoster = spy(new BuildStatusPoster(
                clientFactoryMock.getBitbucketClientFactoryProvider(),
                jenkinsSetupMock.getPluginConfiguration(),
                jenkinsSetupMock.getJenkinsToBitbucketConverter(),
                buildStatusFactory));
        when(buildStatusPoster.useLegacyBuildStatus()).thenReturn(false);

        when(run.getProject()).thenReturn(project);
        when(listener.getLogger()).thenReturn(logger);
        when(buildStatusFactory.prepareBuildStatus(run, action)).thenReturn(buildStatus);
    }

    @Test
    public void testBitbucketClientException() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));
        doThrow(BitbucketClientException.class).when(clientFactoryMock.getBuildStatusClient()).post(any(BitbucketBuildStatus.Builder.class), any());
        buildStatusPoster.onCompleted(run, listener);
        verify(clientFactoryMock.getBuildStatusClient()).post(any(), any());
    }

    @Test
    public void testNoBuildAction() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.emptyList());
        buildStatusPoster.onCompleted(run, listener);
        verifyNoInteractions(jenkinsSetupMock.getPluginConfiguration());
        verifyNoInteractions(listener);
    }

    @Test
    public void testNoMatchingServer() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));
        when(jenkinsSetupMock.getPluginConfiguration().getServerById(SERVER_ID)).thenReturn(Optional.empty());
        buildStatusPoster.onCompleted(run, listener);
        verify(listener).error(eq("Failed to post build status as the provided Bitbucket Server config does not exist"));
        verifyNoInteractions(clientFactoryMock.getBitbucketClientFactoryProvider());
    }

    @Test
    public void testBuildStatusDisabled() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));
        try {
            System.setProperty("bitbucket.status.disable", "true");
            buildStatusPoster.onCompleted(run, listener);
        } finally {
            System.setProperty("bitbucket.status.disable", "");
        }
        verify(logger).println(eq("Build statuses disabled, no build status sent."));
        
        verifyNoInteractions(clientFactoryMock.getBitbucketClientFactoryProvider());
    }

    @Test
    public void testSuccessfulPost() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(eq(buildStatus), any());
        verify(buildStatusFactory).prepareBuildStatus(run, action);
    }

    @Test
    public void testRichBuildStatusForSupportedCapabilities() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));
        when(clientFactoryMock.getCICapabilities().supportsRichBuildStatus()).thenReturn(true);

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(eq(buildStatus), any());
        verify(buildStatusFactory).prepareBuildStatus(run, action);
    }

    @Test
    public void testRichBuildStatusUseLegacyEnabled() {
        when(buildStatusPoster.useLegacyBuildStatus()).thenReturn(true);
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Collections.singletonList(action));
        when(clientFactoryMock.getCICapabilities().supportsRichBuildStatus()).thenReturn(true);

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(eq(buildStatus), any());
        verify(buildStatusFactory).prepareBuildStatus(run, action);
    }

    @Test
    public void testSuccessfulPostMultipleActions() {
        when(run.getActions(BitbucketRevisionAction.class)).thenReturn(Arrays.asList(action, action));

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient(), times(2)).post(eq(buildStatus), any());
        verify(buildStatusFactory, times(2)).prepareBuildStatus(run, action);
    }
}
