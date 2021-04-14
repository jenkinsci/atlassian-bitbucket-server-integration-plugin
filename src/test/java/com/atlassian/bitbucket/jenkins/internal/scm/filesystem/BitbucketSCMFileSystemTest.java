package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
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
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMFileSystemTest {

    static final String SERVER_ID = "SERVER-ID";

    @Rule
    BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();
    JenkinsProjectHandler projectHandler = new JenkinsProjectHandler(bitbucketJenkinsRule);
    @InjectMocks
    BitbucketSCMFileSystem.BuilderImpl builder;
    @Mock
    BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    JenkinsToBitbucketCredentials credentials;
    @Mock
    BitbucketClientFactoryProvider clientFactoryProvider;
    @Mock
    BitbucketServerConfiguration validServerConfiguration;

    @Before
    public void setUp() {
        builder = Jenkins.get().getExtensionList(builder.getClass()).get(0);
        doReturn(FormValidation.Kind.OK).when(validServerConfiguration).validate();
        doReturn(SERVER_ID).when(validServerConfiguration).getId();
        doReturn(validServerConfiguration).when(pluginConfiguration.getServerById(eq(SERVER_ID)));
    }

    @After
    public void cleanUp() {
        projectHandler.cleanup();
    }

    @Test
    public void testSupportsSCMSource() {
        assertThat(builder.supports(mock(BitbucketSCMSource.class)), equalTo(true));
    }

    @Test
    public void testSupportsNonBitbucketSCMSource() {
        assertThat(builder.supports(mock(SCMSource.class)), equalTo(false));
    }

    @Test
    public void testSupportsNonBitbucketSCM() {
        assertThat(builder.supports(mock(SCM.class)), equalTo(false));
    }

    @Test
    public void testSupportsSCM() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        doReturn(Collections.singleton(new BranchSpec("refs/head/master"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(true));
    }

    @Test
    public void testSupportsSCMInvalidBranchSpec() {
        BitbucketSCM pipelineSCM = mock(BitbucketSCM.class);
        // For technical limitations in pipelines, we cannot support wildcards or regex matchers
        doReturn(Collections.singleton(new BranchSpec("**"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(false));

        // We don't support commit hashes- matching against potential branch names opens the possibility
        // to too many false positives.
        doReturn(Collections.singleton(new BranchSpec("0a943a29376f2336b78312d99e65da17048951db"))).when(pipelineSCM).getBranches();
        assertThat(builder.supports(pipelineSCM), equalTo(false));
    }

    @Test
    public void testBuildSCMSource() throws Exception {
        WorkflowMultiBranchProject multiBranchProject = projectHandler.createMultibranchJob("testBuildSCMSource", "PROJECT_1", "rep_1");
        GitBranchSCMHead head = new GitBranchSCMHead("master");
        SCMRevision revision = new GitBranchSCMRevision(head, "");

        SCMFileSystem fileSystem = builder.build(multiBranchProject.getSCMSources().get(0), head, revision);
        assertThat(fileSystem, Matchers.notNullValue());
        BitbucketSCMFile root = ((BitbucketSCMFile)fileSystem.getRoot());
        assertThat(root.getRef().get(), equalTo("refs/heads/master"));
    }

    @Test
    public void testBuildSCMSourceNoServerConfiguration() {
        BitbucketSCMSource scmSource = mock(BitbucketSCMSource.class);
    }

    @Test
    public void testBuildSCMSourceInvalidServerConfiguration() {

    }

    @Test
    public void testBuildSCMSourceInvalidRevision() {

    }
}
