package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
        listener = spy(new LocalSCMListener(buildStatusPoster));
    }

    @Test
    public void testOnCheckoutWithNonGitSCMDoesNotPostBuildStatus() {
        SCM scm = mock(SCM.class);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutFreestyleProjectWithBitbucketSCM() {
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
    public void testOnCheckoutPipelineWithBitbucketSCM() {

        //Can't mock WorkFlow classes so using Project instead.
        Run<?, ?> run = mock(Run.class);
        doReturn(true).when(listener).isWorkflowRun(run);
        Project<?, ?> job = mock(Project.class);
        doReturn(job).when(run).getParent();
        doReturn(singletonList(bitbucketSCM)).when(job).getSCMs();
        when(gitSCM.getKey()).thenReturn("repo-key");

        listener.onCheckout(run, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(run), eq(taskListener));
    }

    @Test
    public void testOnCheckoutMultiBranchProjectWithBitbucketSCM() {

        //Can't mock WorkFlow classes so using Job instead.
        Run<?, ?> run = mock(Run.class);
        doReturn(true).when(listener).isWorkflowRun(run);
        Job<?, ?> job = mock(Job.class);
        MultiBranchProject<?, ?> project = mock(MultiBranchProject.class);
        doReturn(job).when(run).getParent();
        doReturn(project).when(listener).getJobParent(job);
        BranchSource branchSource = mock(BranchSource.class);
        when(project.getSources()).thenReturn(singletonList(branchSource));
        BitbucketSCMSource bbScmSource = mock(BitbucketSCMSource.class);
        when(bbScmSource.getBitbucketSCMRepository()).thenReturn(scmRepository);
        doReturn(true).when(listener).filterSource(gitSCM, bbScmSource);
        when(branchSource.getSource()).thenReturn(bbScmSource);
        when(gitSCM.getKey()).thenReturn("repo-key");

        listener.onCheckout(run, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(run), eq(taskListener));
    }

    @Test
    public void testFilterSourceRemoteURLsMatch() {
        String remoteUrl = "ssh://some-git/remote.git";
        BitbucketSCMSource bitbucketSCMSource = mock(BitbucketSCMSource.class);
        UserRemoteConfig userRemote = mock(UserRemoteConfig.class);
        when(userRemote.getUrl()).thenReturn(remoteUrl);
        List<UserRemoteConfig> userRemotes = singletonList(userRemote);
        when(gitSCM.getUserRemoteConfigs()).thenReturn(userRemotes);
        when(bitbucketSCMSource.getRemote()).thenReturn(remoteUrl);

        assertThat(listener.filterSource(gitSCM, bitbucketSCMSource), is(true));
    }

    @Test
    public void testFilterSourceRemoteURLsDoNotMatch() {
        String gitRemoteUrl = "ssh://some-git/remote.git";
        String bbRemoteUrl = "ssh://some-bb-instance/repo.git";
        BitbucketSCMSource bitbucketSCMSource = mock(BitbucketSCMSource.class);
        UserRemoteConfig userRemote = mock(UserRemoteConfig.class);
        when(userRemote.getUrl()).thenReturn(gitRemoteUrl);
        List<UserRemoteConfig> userRemotes = singletonList(userRemote);
        when(gitSCM.getUserRemoteConfigs()).thenReturn(userRemotes);
        when(bitbucketSCMSource.getRemote()).thenReturn(bbRemoteUrl);

        assertThat(listener.filterSource(gitSCM, bitbucketSCMSource), is(false));
    }
}
