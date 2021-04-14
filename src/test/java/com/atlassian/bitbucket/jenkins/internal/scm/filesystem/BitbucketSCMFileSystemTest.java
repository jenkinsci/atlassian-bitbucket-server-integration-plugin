package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMFileSystemTest {

    @Rule
    public BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();
    BitbucketSCMFileSystem.BuilderImpl builder;
    JenkinsProjectHandler projectHandler = new JenkinsProjectHandler(bitbucketJenkinsRule);

    @After
    public void cleanUp() {
        projectHandler.cleanup();
    }

    @Before
    public void setUp() {
        builder = Jenkins.get().getExtensionList(builder.getClass()).get(0);
    }

    @Test
    public void testBuildPipelineSCM() throws Exception {
        WorkflowJob pipelineProject =
                projectHandler.createPipelineJobWithBitbucketScm("testBuildPipelineSCM", "rep_1", "refs/heads/master");
        BitbucketSCM scm = (BitbucketSCM) ((CpsScmFlowDefinition) pipelineProject.getDefinition()).getScm();

        SCMFileSystem fileSystem = builder.build(pipelineProject, scm, null);
        assertThat(fileSystem, Matchers.notNullValue());
        BitbucketSCMFile root = ((BitbucketSCMFile) fileSystem.getRoot());
        assertThat(root.getRef().get(), equalTo("refs/heads/master"));
    }

    @Test
    public void testBuildPipelineSCMInvalidServerConfiguration() throws Exception {
        String invalidServerID = "INVALID-SERVER-ID";
        BitbucketSCM scm = mock(BitbucketSCM.class);
        BitbucketServerConfiguration invalidConfiguration = mock(BitbucketServerConfiguration.class);
        doReturn(FormValidation.error("")).when(invalidConfiguration).validate();
        //doReturn(Optional.of(invalidConfiguration)).when(pluginConfiguration).getServerById(eq(invalidServerID));
        doReturn(invalidServerID).when(scm).getServerId();

        assertThat(builder.build(mock(Item.class), scm, null), Matchers.nullValue());
    }

    @Test
    public void testBuildPipelineSCMNoServerConfiguration() throws Exception {
        String invalidServerID = "NO-SERVER-ID";
        BitbucketSCM scm = mock(BitbucketSCM.class);
        doReturn(invalidServerID).when(scm).getServerId();

        assertThat(builder.build(mock(Item.class), scm, null), Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSource() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSource", "PROJECT_1", "rep_1");
        GitBranchSCMHead head = new GitBranchSCMHead("master");
        SCMRevision revision = new GitBranchSCMRevision(head, "");

        SCMFileSystem fileSystem = builder.build(multiBranchProject.getSCMSources().get(0), head, revision);
        assertThat(fileSystem, Matchers.notNullValue());
        BitbucketSCMFile root = ((BitbucketSCMFile) fileSystem.getRoot());
        assertThat(root.getRef().get(), equalTo("refs/heads/master"));
    }

    @Test
    public void testBuildSCMSourceInvalidRevision() throws Exception {
        WorkflowMultiBranchProject multiBranchProject =
                projectHandler.createMultibranchJob("testBuildSCMSourceInvalidRevision", "PROJECT_1", "rep_1");
        SCMHead head = mock(SCMHead.class);
        SCMRevision revision = mock(SCMRevision.class);
        doReturn(head).when(revision).getHead();

        assertThat(builder.build(multiBranchProject.getSCMSources().get(0), mock(GitBranchSCMHead.class),
                mock(GitBranchSCMRevision.class)), Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSourceInvalidServerConfiguration() throws Exception {
        String invalidServerID = "INVALID-SERVER-ID";
        BitbucketSCMSource scmSource = mock(BitbucketSCMSource.class);
        BitbucketServerConfiguration invalidConfiguration = mock(BitbucketServerConfiguration.class);
        doReturn(FormValidation.error("")).when(invalidConfiguration).validate();
        //doReturn(Optional.of(invalidConfiguration)).when(pluginConfiguration).getServerById(eq(invalidServerID));
        doReturn(invalidServerID).when(scmSource).getServerId();

        assertThat(builder.build(scmSource, mock(GitBranchSCMHead.class), mock(GitBranchSCMRevision.class)), Matchers.nullValue());
    }

    @Test
    public void testBuildSCMSourceNoServerConfiguration() throws Exception {
        String invalidServerID = "NO-SERVER-ID";
        BitbucketSCMSource scmSource = mock(BitbucketSCMSource.class);
        doReturn(invalidServerID).when(scmSource).getServerId();

        assertThat(builder.build(scmSource, mock(GitBranchSCMHead.class), mock(GitBranchSCMRevision.class)), Matchers.nullValue());
    }

    @Test
    public void testSupportsPipelineSCM() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        doReturn(Collections.singletonList(new BranchSpec("refs/heads/master"))).when(pipelineSCM).getBranches();
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
