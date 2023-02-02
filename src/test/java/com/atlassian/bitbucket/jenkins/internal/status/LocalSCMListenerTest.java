package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.provider.GlobalLibrariesProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepositoryHelper;
import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner.Silent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Silent.class)
public class LocalSCMListenerTest extends HudsonTestCase {

    private final Map<String, String> buildMap = new HashMap<>();
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BuildStatusPoster buildStatusPoster;
    @Mock
    private GitSCM gitSCM;
    @Mock
    private GlobalLibraries globalLibraries;
    @Mock
    private GlobalLibrariesProvider librariesProvider;
    private LocalSCMListener listener;
    @Mock
    private BitbucketSCMRepositoryHelper repositoryHelper;
    @Mock
    private AbstractBuild run;
    @Mock
    private BitbucketSCMRepository scmRepository;
    @Mock
    private TaskListener taskListener;

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
        listener = spy(new LocalSCMListener(buildStatusPoster, librariesProvider, repositoryHelper));
        when(librariesProvider.get()).thenReturn(globalLibraries);
    }

    @Test
    public void testOnCheckoutMultiBranchPipelineWithFolderLibraryDoesNotPostBuildStatus() throws IOException {
        when(bitbucketSCM.getId()).thenReturn("SomeId");
        WorkflowMultiBranchProject workflowMultiBranchProject =
                jenkinsRule.getInstance().createProject(WorkflowMultiBranchProject.class, "MultiBranchProject");
        WorkflowJob workflowJob = new WorkflowJob(workflowMultiBranchProject, "Job1");
        WorkflowRun workflowRun = new WorkflowRun(workflowJob);
        LibraryConfiguration libConfig = mock(LibraryConfiguration.class);
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libConfig.getRetriever()).thenReturn(scmRetriever);
        when(scmRetriever.getScm()).thenReturn(bitbucketSCM);
        FolderLibraries folderLibraries = new FolderLibraries(singletonList(libConfig));
        workflowMultiBranchProject.addProperty(folderLibraries);

        listener.onCheckout(workflowRun, bitbucketSCM, null, taskListener, null, null);

        // Twice since the same ID will be compared to itself.
        verify(bitbucketSCM, times(2)).getId();
        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithBitbucketSCM() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getParent()).thenReturn(project);
        when(globalLibraries.getLibraries()).thenReturn(emptyList());

        listener.onCheckout(build, bitbucketSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(build), eq(taskListener));
    }

    @Test
    public void testOnCheckoutWithFolderLibraryDoesNotPostBuildStatus() throws Exception {
        when(bitbucketSCM.getId()).thenReturn("SomeID");
        Folder folder = new Folder(jenkinsRule.getInstance().getItemGroup(), "Folder");
        WorkflowJob workflowJob = new WorkflowJob(folder, "Job1");
        LibraryConfiguration libConfig = mock(LibraryConfiguration.class);
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libConfig.getRetriever()).thenReturn(scmRetriever);
        when(scmRetriever.getScm()).thenReturn(bitbucketSCM);
        FolderLibraries folderLibraries = new FolderLibraries(singletonList(libConfig));
        folder.addProperty(folderLibraries);
        WorkflowRun workflowRun = new WorkflowRun(workflowJob);

        listener.onCheckout(workflowRun, bitbucketSCM, null, taskListener, null, null);

        // Twice since the same ID will be compared to itself.
        verify(bitbucketSCM, times(2)).getId();
        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithGitSCM() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        FreeStyleBuild build = mock(FreeStyleBuild.class);
        when(build.getParent()).thenReturn(project);
        when(globalLibraries.getLibraries()).thenReturn(emptyList());

        listener.onCheckout(build, gitSCM, null, taskListener, null, null);

        verify(buildStatusPoster).postBuildStatus(
                argThat(revision ->
                        scmRepository.equals(revision.getBitbucketSCMRepo())),
                eq(build), eq(taskListener));
    }

    @Test
    public void testOnCheckoutWithGlobalLibrariesDoesNotPostBuildStatus() {
        LibraryConfiguration libraryConfiguration = mock(LibraryConfiguration.class);
        when(globalLibraries.getLibraries()).thenReturn(singletonList(libraryConfiguration));
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libraryConfiguration.getRetriever()).thenReturn(scmRetriever);
        when(scmRetriever.getScm()).thenReturn(bitbucketSCM);
        FreeStyleProject project = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(project);
        when(bitbucketSCM.getId()).thenReturn("SomeID");

        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);
        // Twice since the same ID will be compared to itself.
        verify(bitbucketSCM, times(2)).getId();
        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithGlobalLibrariesPostToNonLibrarySCM() {
        LibraryConfiguration libraryConfiguration = mock(LibraryConfiguration.class);
        when(globalLibraries.getLibraries()).thenReturn(singletonList(libraryConfiguration));
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libraryConfiguration.getRetriever()).thenReturn(scmRetriever);
        BitbucketSCM scm = mock(BitbucketSCM.class);
        when(scmRetriever.getScm()).thenReturn(scm);
        when(scm.getId()).thenReturn("OtherID");
        FreeStyleProject project = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(project);
        when(bitbucketSCM.getId()).thenReturn("SomeID");
        when(repositoryHelper.getRepository(run, bitbucketSCM)).thenReturn(scmRepository);

        listener.onCheckout(run, bitbucketSCM, null, taskListener, null, null);

        verify(bitbucketSCM, times(1)).getId();
        verify(scm, times(1)).getId();
        verify(buildStatusPoster, times(1)).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithNestedFolderLibraryDoesNotPostBuildStatus() throws IOException {
        when(bitbucketSCM.getId()).thenReturn("SomeID");
        Folder parentFolder = new Folder(jenkinsRule.getInstance().getItemGroup(), "ParentFolder");
        LibraryConfiguration libConfig = mock(LibraryConfiguration.class);
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libConfig.getRetriever()).thenReturn(scmRetriever);
        when(scmRetriever.getScm()).thenReturn(bitbucketSCM);
        FolderLibraries folderLibraries = new FolderLibraries(singletonList(libConfig));
        parentFolder.addProperty(folderLibraries);
        Folder folder = new Folder(parentFolder, "Folder");
        WorkflowJob workflowJob = new WorkflowJob(folder, "Job1");
        WorkflowRun workflowRun = new WorkflowRun(workflowJob);

        listener.onCheckout(workflowRun, bitbucketSCM, null, taskListener, null, null);

        // Twice since the same ID will be compared to itself.
        verify(bitbucketSCM, times(2)).getId();
        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithNestedFolderLibraryUnderMultiBranchDoesNotPostBuildStatus() throws IOException {
        when(bitbucketSCM.getId()).thenReturn("SomeID");
        Folder parentFolder = new Folder(jenkinsRule.getInstance().getItemGroup(), "ParentFolder");
        LibraryConfiguration libConfig = mock(LibraryConfiguration.class);
        SCMRetriever scmRetriever = mock(SCMRetriever.class);
        when(libConfig.getRetriever()).thenReturn(scmRetriever);
        when(scmRetriever.getScm()).thenReturn(bitbucketSCM);
        FolderLibraries folderLibraries = new FolderLibraries(singletonList(libConfig));
        parentFolder.addProperty(folderLibraries);
        WorkflowMultiBranchProject workflowMultiBranchProject = new WorkflowMultiBranchProject(parentFolder, "name");
        WorkflowJob workflowJob = new WorkflowJob(workflowMultiBranchProject, "Job1");
        WorkflowRun workflowRun = new WorkflowRun(workflowJob);

        listener.onCheckout(workflowRun, bitbucketSCM, null, taskListener, null, null);

        // Twice since the same ID will be compared to itself.
        verify(bitbucketSCM, times(2)).getId();
        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithNoRepositoryDoesNotPostBuildStatus() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(project);
        when(globalLibraries.getLibraries()).thenReturn(emptyList());
        SCM scm = mock(SCM.class);

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }

    @Test
    public void testOnCheckoutWithNonGitSCMDoesNotPostBuildStatus() {
        SCM scm = mock(SCM.class);
        when(repositoryHelper.getRepository(run, scm)).thenReturn(scmRepository);
        FreeStyleProject project = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(project);
        when(globalLibraries.getLibraries()).thenReturn(emptyList());

        listener.onCheckout(run, scm, null, taskListener, null, null);

        verify(buildStatusPoster, never()).postBuildStatus(any(), any(), any());
    }
}
