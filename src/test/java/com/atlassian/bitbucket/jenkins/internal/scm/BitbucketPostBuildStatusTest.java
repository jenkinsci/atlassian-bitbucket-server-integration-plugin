package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster;
import com.google.inject.Injector;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPostBuildStatusTest {

    private static final String SERVER_ID = "TestServerID";
    private static final String SHA1 = "67d71c2133aab0e070fb8100e3e71220332c5af1";
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AbstractBuild<?, ?> build;
    @Mock
    private BuildData buildData;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Captor
    private ArgumentCaptor<BitbucketRevisionAction> captor;
    private BitbucketPostBuildStatus extension;
    @Mock
    private GitClient gitClient;
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
    private PrintStream logger;
    @Mock
    private Run<FreeStyleProject, ?> notABuild;
    @Mock
    private ObjectId objectId;
    @Mock
    private Revision revision;
    @Mock
    private BitbucketRevisionAction revisionAction;
    @Mock
    private GitSCM scm;

    @Before
    public void setup() {
        extension = new BitbucketPostBuildStatus(SERVER_ID, jenkinsProvider);
        buildData.lastBuild = lastBuild;
        when(lastBuild.getRevision()).thenReturn(revision);
        when(revision.getSha1String()).thenReturn(SHA1);
        when(jenkinsProvider.get()).thenReturn(jenkins);
        when(jenkins.getInjector()).thenReturn(injector);
        when(listener.getLogger()).thenReturn(logger);
        when(build.getAction(any())).thenReturn(revisionAction);
    }

    @Test
    public void testNoInjector() {
        when(jenkins.getInjector()).thenReturn(null);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(build, never()).addAction(any());
        verifyZeroInteractions(buildStatusPoster);
        verify(logger).println("Injector could not be found while creating build status");
    }

    @Test
    public void testNoPoster() {
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(logger).println("Build Status Poster instance could not be found while creating a build status");
    }

    @Test
    public void testPostStatus() {
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(build).addAction(captor.capture());
        BitbucketRevisionAction action = captor.getValue();
        assertThat(action.getServerId(), equalTo(SERVER_ID));
        assertThat(action.getRevisionSha1(), equalTo(SHA1));
        verify(buildStatusPoster).postBuildStatus(build, listener);
    }

    @Test
    public void testRunNotOfTypeBuild() {
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verifyZeroInteractions(buildStatusPoster);
    }

    @Test
    public void testExtractingBranch() {
        String ref = "refs/heads/master";
        when(revision.getBranches()).thenReturn(Collections.singletonList(new Branch(ref, objectId)));
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(build).addAction(captor.capture());
        BitbucketRevisionAction action = captor.getValue();
        assertThat(action.getRefName(), equalTo(ref));
    }

    @Test
    public void testExtractBranchWithoutRefsPrefix() {
        String ref = "master";
        when(revision.getBranches()).thenReturn(Collections.singletonList(new Branch(ref, objectId)));
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(build).addAction(captor.capture());
        BitbucketRevisionAction action = captor.getValue();
        assertThat(action.getRefName(), equalTo("refs/heads/" + ref));
    }

    @Test
    public void testMultipleBranches() {
        String ref = "refs/heads/master";
        List<Branch> branches = Arrays.asList(new Branch(ref, objectId),
                new Branch("refs/heads/featureBranch", objectId),
                new Branch("refs/heads/feature/branch", objectId));
        when(revision.getBranches()).thenReturn(branches);
        when(injector.getInstance(BuildStatusPoster.class)).thenReturn(buildStatusPoster);
        when(scm.getBuildData(build)).thenReturn(buildData);
        extension.decorateRevisionToBuild(scm, build, gitClient, listener, revision, revision);
        verify(build).addAction(captor.capture());
        BitbucketRevisionAction action = captor.getValue();
        assertThat(action.getRefName(), equalTo(ref));
    }
}