package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BitbucketSCMRepositoryHelperTest {

    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private GitSCM gitSCM;
    @Spy
    private BitbucketSCMRepositoryHelper scmRepositoryHelper;
    @Mock
    private BitbucketSCMRepository scmRepository;

    @Before
    public void setup() {
        when(bitbucketSCM.getGitSCM()).thenReturn(gitSCM);
        when(bitbucketSCM.getBitbucketSCMRepository()).thenReturn(scmRepository);
    }

    @Test
    public void testGetRepositoryForPipelineWithBitbucketSCM() {
        //Can't mock WorkFlow classes so using Project instead.
        Run<?, ?> run = mock(Run.class);
        doReturn(true).when(scmRepositoryHelper).isWorkflowRun(run);
        Project<?, ?> job = mock(Project.class);
        doReturn(job).when(run).getParent();
        doReturn(singletonList(bitbucketSCM)).when(job).getSCMs();
        when(gitSCM.getKey()).thenReturn("repo-key");

        BitbucketSCMRepository repository = scmRepositoryHelper.getRepository(run, gitSCM);

        assertThat(repository, equalTo(scmRepository));
    }

    @Test
    public void testGetRepositoryMultiBranchProjectWithBitbucketSCM() {
        //Can't mock WorkFlow classes so using Job instead.
        Run<?, ?> run = mock(Run.class);
        doReturn(true).when(scmRepositoryHelper).isWorkflowRun(run);
        Job<?, ?> job = mock(Job.class);
        MultiBranchProject<?, ?> project = mock(MultiBranchProject.class);
        doReturn(job).when(run).getParent();
        doReturn(project).when(scmRepositoryHelper).getJobParent(job);
        BranchSource branchSource = mock(BranchSource.class);
        when(project.getSources()).thenReturn(singletonList(branchSource));
        BitbucketSCMSource bbScmSource = mock(BitbucketSCMSource.class);
        when(bbScmSource.getBitbucketSCMRepository()).thenReturn(scmRepository);
        doReturn(true).when(scmRepositoryHelper).filterSource(gitSCM, bbScmSource);
        when(branchSource.getSource()).thenReturn(bbScmSource);
        when(gitSCM.getKey()).thenReturn("repo-key");

        BitbucketSCMRepository repository = scmRepositoryHelper.getRepository(run, gitSCM);

        assertThat(repository, equalTo(scmRepository));
    }

    @Test
    public void testGetRepositoryNonRelatedSCM() {
        Run<?, ?> run = mock(Run.class);
        SCM scm = mock(SCM.class);

        BitbucketSCMRepository repository = scmRepositoryHelper.getRepository(run, scm);

        assertNull(repository);
    }

    @Test
    public void testGetRepositoryBitbucketSCM() {
        Run<?, ?> run = mock(Run.class);

        BitbucketSCMRepository repository = scmRepositoryHelper.getRepository(run, bitbucketSCM);

        assertThat(repository, equalTo(scmRepository));
    }

    @Test
    public void testGetRepositoryGitScmOnNotWorkflowRun() {
        //Can't mock WorkFlow classes so using Job instead.
        Run<?, ?> run = mock(Run.class);
        doReturn(false).when(scmRepositoryHelper).isWorkflowRun(run);
        Job<?, ?> job = mock(Job.class);
        MultiBranchProject<?, ?> project = mock(MultiBranchProject.class);
        doReturn(job).when(run).getParent();
        doReturn(project).when(scmRepositoryHelper).getJobParent(job);
        BranchSource branchSource = mock(BranchSource.class);
        when(project.getSources()).thenReturn(singletonList(branchSource));
        BitbucketSCMSource bbScmSource = mock(BitbucketSCMSource.class);
        when(bbScmSource.getBitbucketSCMRepository()).thenReturn(scmRepository);
        doReturn(true).when(scmRepositoryHelper).filterSource(gitSCM, bbScmSource);
        when(branchSource.getSource()).thenReturn(bbScmSource);
        when(gitSCM.getKey()).thenReturn("repo-key");

        BitbucketSCMRepository repository = scmRepositoryHelper.getRepository(run, gitSCM);

        assertNull(repository);
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

        assertThat(scmRepositoryHelper.filterSource(gitSCM, bitbucketSCMSource), is(true));
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

        assertThat(scmRepositoryHelper.filterSource(gitSCM, bitbucketSCMSource), is(false));
    }
}