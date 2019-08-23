package com.atlassian.bitbucket.jenkins.internal.scm.extensions;

import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster;
import com.google.inject.Injector;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPostBuildStatusTest {

    private static final String SERVER_ID = "TestServerID";
    private static final String SHA1 = "67d71c2133aab0e070fb8100e3e71220332c5af1";
    @Mock
    private AbstractBuild build;
    @Mock
    private BuildData buildData;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Captor
    private ArgumentCaptor<BitbucketRevisionAction> captor;
    private BitbucketPostBuildStatus extension;
    @Mock
    private Injector injector;
    @Mock
    private Jenkins jenkins;
    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Build lastBuild;
    @Mock
    private TaskListener listener;
    @Mock
    private Run notABuild;
    @Mock
    private Revision revision;
    @Mock
    private GitSCM scm;

    @Before
    public void setup() {
        extension = new BitbucketPostBuildStatus(SERVER_ID, jenkinsProvider);
        buildData.lastBuild = lastBuild;
        when(lastBuild.getRevision()).thenReturn(revision);
        when(revision.getSha1String()).thenReturn(SHA1);
        when(jenkinsProvider.get()).thenReturn(jenkins);
    }

    @Test
    public void testNoBuildData() {
        when(jenkins.getInjector()).thenReturn(injector);
        extension.decorateCheckoutCommand(scm, build, null, listener, null);
        verifyZeroInteractions(buildStatusPoster);
    }

    @Test
    public void testNoInjector() {
        extension.decorateCheckoutCommand(scm, build, null, listener, null);
        verifyZeroInteractions(build);
        verifyZeroInteractions(buildStatusPoster);
    }

    @Test
    public void testNoPoster() {
        when(jenkins.getInjector()).thenReturn(injector);
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateCheckoutCommand(scm, build, null, listener, null);
    }

    @Test
    public void testNullBuild() {
        when(jenkins.getInjector()).thenReturn(injector);
        extension.decorateCheckoutCommand(scm, null, null, listener, null);
        verifyZeroInteractions(buildStatusPoster);
    }

    @Test
    public void testPostStatus() {
        when(jenkins.getInjector()).thenReturn(injector);
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateCheckoutCommand(scm, build, null, listener, null);
        verify(build).addAction(captor.capture());
        BitbucketRevisionAction action = captor.getValue();
        assertThat(action.getServerId(), equalTo(SERVER_ID));
        assertThat(action.getRevisionSha1(), equalTo(SHA1));
        verify(buildStatusPoster).postBuildStatus(build, listener);
    }

    @Test
    public void testRunNotOfTypeBuild() {
        when(jenkins.getInjector()).thenReturn(injector);
        extension.decorateCheckoutCommand(scm, notABuild, null, listener, null);
        verifyZeroInteractions(buildStatusPoster);
    }
}