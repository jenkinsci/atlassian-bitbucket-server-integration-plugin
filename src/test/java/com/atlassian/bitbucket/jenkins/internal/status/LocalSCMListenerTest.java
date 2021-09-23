package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepositoryHelper;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Silent.class)
public class LocalSCMListenerTest {

    @Mock
    private GitSCM gitSCM;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Mock
    private AbstractBuild run;
    @Mock
    private TaskListener taskListener;
    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BitbucketSCMRepository scmRepository;
    @Mock
    private BitbucketSCMRepositoryHelper repositoryHelper;
    private LocalSCMListener listener;
    private Map<String, String> buildMap = new HashMap<>();

    @Before
    public void setup() throws URISyntaxException {
        buildMap.put(GitSCM.GIT_BRANCH, "master");
        buildMap.put(GitSCM.GIT_COMMIT, "c1");
        when(bitbucketSCM.getGitSCM()).thenReturn(gitSCM);
        doAnswer(invocation -> {
            Map<String, String> m = (Map<String, String>) invocation.getArguments()[1];
            m.putAll(buildMap);
            return null;
        }).when(gitSCM).buildEnvironment(notNull(), anyMap());
        RemoteConfig rc = new RemoteConfig(new Config(), "origin");
        when(gitSCM.getRepositories()).thenReturn(singletonList(rc));
        when(scmRepository.getRepositorySlug()).thenReturn("repo1");
        when(bitbucketSCM.getServerId()).thenReturn("ServerId");
        when(bitbucketSCM.getBitbucketSCMRepository()).thenReturn(scmRepository);
        when(repositoryHelper.getRepository(any(), eq(bitbucketSCM))).thenReturn(scmRepository);
        when(repositoryHelper.getRepository(any(), eq(gitSCM))).thenReturn(scmRepository);
        listener = spy(new LocalSCMListener(buildStatusPoster, repositoryHelper));
    }

    @Test
    public void testOnCheckoutWithNonGitSCMDoesNotPostBuildStatus() {
        SCM scm = mock(SCM.class);
        when(repositoryHelper.getRepository(run, scm)).thenReturn(scmRepository);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithNoRepositoryDoesNotPostBuildStatus() {
        SCM scm = mock(SCM.class);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithBitbucketSCM() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getParent()).thenReturn(project);

        listener.onCheckout(build, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(build), eq(taskListener));
    }

    @Test
    public void testOnCheckoutWithGitSCM() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getParent()).thenReturn(project);

        listener.onCheckout(build, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(build), eq(taskListener));
    }
}
