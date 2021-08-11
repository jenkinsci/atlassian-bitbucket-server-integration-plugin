package it.com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFileSystem;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMFileSystemIT {

    @Rule
    public BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();
    @InjectMocks
    BitbucketSCMFileSystem.BuilderImpl builder;
    @Mock
    BitbucketClientFactoryProvider clientFactoryProvider;
    BitbucketServerConfiguration invalidConfiguration;
    @Mock
    JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    BitbucketPluginConfiguration pluginConfiguration;
    JenkinsProjectHandler projectHandler = new JenkinsProjectHandler(bitbucketJenkinsRule);
    BitbucketServerConfiguration validConfiguration;

    @After
    public void cleanUp() {
        projectHandler.cleanup();
    }

    @Before
    public void setUp() {
        invalidConfiguration = mock(BitbucketServerConfiguration.class);
        validConfiguration = mock(BitbucketServerConfiguration.class);
        doReturn(FormValidation.ok()).when(validConfiguration).validate();
        doReturn(FormValidation.error("")).when(invalidConfiguration).validate();

        BitbucketClientFactory clientFactory = mock(BitbucketClientFactory.class);
        BitbucketProjectClient projectClient = mock(BitbucketProjectClient.class);
        BitbucketRepositoryClient repositoryClient = mock(BitbucketRepositoryClient.class);

        doReturn(clientFactory).when(clientFactoryProvider).getClient(any(), any());
        doReturn(projectClient).when(clientFactory).getProjectClient("PROJECT_1");
        doReturn(repositoryClient).when(projectClient).getRepositoryClient("rep_1");
        doReturn(mock(BitbucketFilePathClient.class)).when(repositoryClient).getFilePathClient();
    }

    @Test
    public void testBuildPipelineSCM() throws Exception {
        WorkflowJob pipelineProject =
                projectHandler.createPipelineJobWithBitbucketScm("testBuildPipelineSCM", "project_1", "rep_1", "refs/heads/master");
        BitbucketSCM scm = (BitbucketSCM) ((CpsScmFlowDefinition) pipelineProject.getDefinition()).getScm();
        doReturn(Optional.of(validConfiguration)).when(pluginConfiguration).getServerById(eq(scm.getServerId()));

        SCMFileSystem fileSystem = builder.build(pipelineProject, scm, null);
        assertThat(fileSystem, Matchers.notNullValue());
        BitbucketSCMFile root = ((BitbucketSCMFile) fileSystem.getRoot());
        assertThat(root.getRef().get(), equalTo("refs/heads/master"));

        verify(jenkinsToBitbucketCredentials).toBitbucketCredentials(any(String.class), eq(pipelineProject));
    }

    @Test
    public void testBuildPipelineSCMInvalidServerConfiguration() {
        BitbucketSCM scm = mock(BitbucketSCM.class);
        doReturn("INVALID-CONFIG-ID").when(scm).getServerId();
        doReturn(Optional.of(invalidConfiguration)).when(pluginConfiguration).getServerById("INVALID-CONFIG-ID");

        assertThat(builder.build(mock(Item.class), scm, null), Matchers.nullValue());
    }

    @Test
    public void testBuildPipelineSCMNoServerConfiguration() {
        BitbucketSCM scm = mock(BitbucketSCM.class);
        doReturn("ABSENT-SERVER-ID").when(scm).getServerId();

        assertThat(builder.build(mock(Item.class), scm, null), Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSource() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSource", "PROJECT_1", "rep_1");
        GitBranchSCMHead head = new GitBranchSCMHead("master");
        SCMRevision revision = new GitBranchSCMRevision(head, "");

        String serverConfigID = ((BitbucketSCMSource) multiBranchProject.getSCMSources().get(0)).getServerId();
        doReturn(Optional.of(validConfiguration)).when(pluginConfiguration).getServerById(eq(serverConfigID));

        SCMFileSystem fileSystem = builder.build(multiBranchProject.getSCMSources().get(0), head, revision);
        assertThat(fileSystem, Matchers.notNullValue());
        BitbucketSCMFile root = ((BitbucketSCMFile) fileSystem.getRoot());
        assertThat(root.getRef().get(), equalTo("refs/heads/master"));

        verify(jenkinsToBitbucketCredentials).toBitbucketCredentials(any(String.class), eq(multiBranchProject));
    }

    @Test
    public void testBuildSCMSourceInvalidRevision() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSourceInvalidRevision", "PROJECT_1", "rep_1");
        SCMRevision revision = new GitBranchSCMRevision(mock(GitBranchSCMHead.class), "");

        assertThat(builder.build(multiBranchProject.getSCMSources().get(0), revision.getHead(), revision),
                Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSourceInvalidServerConfiguration() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSourceInvalidServerConfiguration", "PROJECT_1", "rep_1");
        BitbucketSCMSource scmSource = (BitbucketSCMSource) multiBranchProject.getSCMSources().get(0);

        doReturn(Optional.of(invalidConfiguration)).when(pluginConfiguration).getServerById(scmSource.getServerId());

        assertThat(builder.build(scmSource, mock(GitBranchSCMHead.class), mock(GitBranchSCMRevision.class)), Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSourceNoServerConfiguration() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSourceNoServerConfiguration", "PROJECT_1", "rep_1");
        BitbucketSCMSource scmSource = (BitbucketSCMSource) multiBranchProject.getSCMSources().get(0);

        doReturn(Optional.empty()).when(pluginConfiguration).getServerById(scmSource.getServerId());

        assertThat(builder.build(scmSource, mock(GitBranchSCMHead.class), mock(GitBranchSCMRevision.class)), Matchers.nullValue());
    }

    @Test
    public void testSupportsPipelineSCMRefsHeads() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        doReturn(Collections.singletonList(new BranchSpec("refs/heads/master"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(true));
    }

    @Test
    public void testSupportsPipelineSCMRefsTags() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        doReturn(Collections.singletonList(new BranchSpec("refs/tags/release-2.1"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(true));
    }

    @Test
    public void testSupportsPipelineSCMInvalidBranchSpec() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        // For technical limitations in pipelines, we cannot support wildcards or regex matchers
        doReturn(Collections.singletonList(new BranchSpec("**"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(false));

        // We don't support commit hashes- matching against potential branch names opens the possibility
        // to too many false positives.
        doReturn(Collections.singletonList(new BranchSpec("0a943a29376f2336b78312d99e65da17048951db"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(false));
    }

    @Test
    public void testSupportsPipelineSCMNotBitbucket() {
        assertThat(builder.supports(mock(SCM.class)), equalTo(false));
    }

    @Test
    public void testSupportsSCMSource() {
        assertThat(builder.supports(mock(BitbucketSCMSource.class)), equalTo(true));
    }

    @Test
    public void testSupportsSCMSourceNotBitbucket() {
        assertThat(builder.supports(mock(SCMSource.class)), equalTo(false));
    }
}
