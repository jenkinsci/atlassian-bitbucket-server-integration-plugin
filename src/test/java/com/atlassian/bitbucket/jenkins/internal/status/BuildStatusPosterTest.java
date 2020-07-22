package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.fixture.mocks.BitbucketJenkinsSetup;
import com.atlassian.bitbucket.jenkins.internal.fixture.mocks.TestBitbucketClientFactoryHandler;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
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
            new BitbucketSCMRepository(null, PROJECT_NAME, PROJECT_NAME, REPO_SLUG, REPO_SLUG, SERVER_ID, "");
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

    private BitbucketBuildStatus buildStatus = new BitbucketBuildStatus.Builder("key", SUCCESSFUL, "aUrl").build();
    private TestBitbucketClientFactoryHandler clientFactoryMock;
    private BitbucketJenkinsSetup jenkinsSetupMock;
    private BuildStatusPoster buildStatusPoster;

    @Before
    public void setup() {
        jenkinsSetupMock = BitbucketJenkinsSetup.create().assignGlobalCredentialProviderToItem(project);
        clientFactoryMock =
                TestBitbucketClientFactoryHandler.create(jenkinsSetupMock, jenkinsSetupMock.getBbAdminCredentials())
                        .withBuildStatusClient(REVISION_SHA1, scmRepository)
                        .withCICapabilities(BitbucketCICapabilities.RICH_BUILD_STATUS_CAPABILITY);

        buildStatusPoster = spy(new BuildStatusPoster(
                clientFactoryMock.getBitbucketClientFactoryProvider(),
                jenkinsSetupMock.getPluginConfiguration(),
                jenkinsSetupMock.getJenkinsToBitbucketConverter(),
                buildStatusFactory));
        when(buildStatusPoster.useLegacyBuildStatus()).thenReturn(false);

        when(run.getProject()).thenReturn(project);
        when(listener.getLogger()).thenReturn(logger);
        when(buildStatusFactory.createRichBuildStatus(run)).thenReturn(buildStatus);
        when(buildStatusFactory.createLegacyBuildStatus(run)).thenReturn(buildStatus);
    }

    @Test
    public void testBitbucketClientException() {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        doThrow(BitbucketClientException.class).when(clientFactoryMock.getBuildStatusClient()).post(any(BitbucketBuildStatus.class));
        buildStatusPoster.onCompleted(run, listener);
        verify(clientFactoryMock.getBuildStatusClient()).post(any());
    }

    @Test
    public void testNoBuildAction() {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(null);
        buildStatusPoster.onCompleted(run, listener);
        verifyZeroInteractions(jenkinsSetupMock.getPluginConfiguration());
        verifyZeroInteractions(listener);
    }

    @Test
    public void testNoMatchingServer() {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(jenkinsSetupMock.getPluginConfiguration().getServerById(SERVER_ID)).thenReturn(Optional.empty());
        buildStatusPoster.onCompleted(run, listener);
        verify(listener).error(eq("Failed to post build status as the provided Bitbucket Server config does not exist"));
        verifyZeroInteractions(clientFactoryMock.getBitbucketClientFactoryProvider());
    }

    @Test
    public void testSuccessfulPost() {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(action);

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(buildStatus);
        verify(buildStatusFactory).createLegacyBuildStatus(run);
    }

    @Test
    public void testRichBuildStatusForSupportedCapabilities() {
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(clientFactoryMock.getCICapabilities().supportsRichBuildStatus()).thenReturn(true);

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(buildStatus);
        verify(buildStatusFactory).createRichBuildStatus(run);
    }

    @Test
    public void testRichBuildStatusUseLegacyEnabled() {
        when(buildStatusPoster.useLegacyBuildStatus()).thenReturn(true);
        when(run.getAction(BitbucketRevisionAction.class)).thenReturn(action);
        when(clientFactoryMock.getCICapabilities().supportsRichBuildStatus()).thenReturn(true);

        buildStatusPoster.onCompleted(run, listener);

        verify(clientFactoryMock.getBuildStatusClient()).post(buildStatus);
        verify(buildStatusFactory).createLegacyBuildStatus(run);
    }
}
