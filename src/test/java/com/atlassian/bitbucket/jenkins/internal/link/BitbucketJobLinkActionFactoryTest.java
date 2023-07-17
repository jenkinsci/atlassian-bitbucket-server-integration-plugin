package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultSCMHeadByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultSCMSourceByItemProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketPullRequestSCMHead;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketJobLinkActionFactoryTest {

    private static final String SERVER_ID = "Test-Server-ID";
    private static final String BASE_URL = "http://localhost:8080/bitbucket";

    private BitbucketJobLinkActionFactory actionFactory;
    private BitbucketExternalLinkUtils externalLinkUtils;
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    @Mock
    BitbucketSCM scm;
    @Mock
    private BitbucketSCMRepository bitbucketRepository;
    @Mock
    private BitbucketServerConfiguration configuration;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private FreeStyleProject freeStyleProject;
    @Mock
    private DefaultSCMHeadByItemProvider headProvider;
    @Mock
    private DefaultSCMSourceByItemProvider sourceProvider;

    private WorkflowJob workflowJob;
    private WorkflowJob workflowJobWithScmStep;
    private WorkflowJob multibranchJobFromSource;
    @Mock
    private WorkflowMultiBranchProject multibranchProject;

    @Before
    public void init() throws IOException {
        when(scm.getBitbucketSCMRepository()).thenReturn(bitbucketRepository);
        BitbucketSCMSource mockSCMSource = mock(BitbucketSCMSource.class);
        when(mockSCMSource.getBitbucketSCMRepository()).thenReturn(bitbucketRepository);
        when(bitbucketRepository.getProjectKey()).thenReturn("PROJ");
        when(bitbucketRepository.getRepositorySlug()).thenReturn("repo");
        when(bitbucketRepository.getServerId()).thenReturn(SERVER_ID);

        workflowJob = jenkins.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));
        workflowJobWithScmStep = jenkins.createProject(WorkflowJob.class, "workflow-job-with-scm-step");
        multibranchJobFromSource = jenkins.createProject(WorkflowJob.class, "branch2");

        when(freeStyleProject.getScm()).thenReturn(scm);

        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(configuration));
        when(configuration.getBaseUrl()).thenReturn(BASE_URL);
        when(configuration.validate()).thenReturn(FormValidation.ok());

        SCMHead multibranchJobFromSourceHead = mock(SCMHead.class);
        doReturn("branch2").when(multibranchJobFromSourceHead).getName();
        doReturn(multibranchJobFromSourceHead).when(headProvider).findHead(multibranchJobFromSource);
        doReturn(mockSCMSource).when(sourceProvider).findSource(multibranchJobFromSource);

        externalLinkUtils = new BitbucketExternalLinkUtils(pluginConfiguration);
        actionFactory = getActionFactory();
    }

    @Test
    public void testCreateFreestyle() {
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateWorkflow() {
        Collection<? extends Action> actions = actionFactory.createFor(workflowJob);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateWorkflowCustomStep() {
        Collection<? extends Action> actions = actionFactory.createFor(workflowJobWithScmStep);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateMultibranchSource() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchJobFromSource);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo/compare/commits?sourceBranch=refs%2Fheads%2Fbranch2"));
    }

    @Test
    public void testCreateMultibranchSourcePullRequestHead() {
        BitbucketProject project = new BitbucketProject("PROJ", null, "PROJ");
        BitbucketRepository repository = new BitbucketRepository(0,
                "repo",
                null,
                project,
                "repo",
                RepositoryState.AVAILABLE);
        BitbucketPullRequestRef fromRef = new BitbucketPullRequestRef("heads/refs/from",
                "from",
                repository,
                "fromCommit");
        BitbucketPullRequestRef toRef = new BitbucketPullRequestRef("heads/refs/to",
                "to",
                repository,
                "toCommit");
        BitbucketPullRequest pullRequest = new BitbucketPullRequest(1,
                BitbucketPullRequestState.OPEN,
                fromRef,
                toRef,
                -1,
                "Test pull request",
                "This is a test pull request");
        BitbucketPullRequestSCMHead prHead = new BitbucketPullRequestSCMHead(pullRequest);
        doReturn(prHead).when(headProvider).findHead(multibranchJobFromSource);

        Collection<? extends Action> actions = actionFactory.createFor(multibranchJobFromSource);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo/pull-requests/1"));
    }

    @Test
    public void testCreateNotBitbucketSCMFreestyle() {
        when(freeStyleProject.getScm()).thenReturn(mock(SCM.class));
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateNotBitbucketSCMWorkflow() {
        workflowJob.setDefinition(new CpsScmFlowDefinition(mock(SCM.class), "Jenkinsfile"));
        Collection<? extends Action> actions = actionFactory.createFor(workflowJob);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerConfigurationInvalid() {
        when(configuration.validate()).thenReturn(FormValidation.error("config invalid"));
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerNotConfigured() {
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.empty());
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }

    private BitbucketJobLinkActionFactory getActionFactory() {
        return new BitbucketJobLinkActionFactory(externalLinkUtils, headProvider, sourceProvider) {

            @Override
            Collection<? extends SCM> getWorkflowSCMs(WorkflowJob job) {
                if (Objects.equals(job, workflowJobWithScmStep)) {
                    return Collections.singleton(scm);
                }
                return Collections.emptySet();
            }

            @Override
            ItemGroup getWorkflowParent(WorkflowJob job) {
                if (Objects.equals(job, multibranchJobFromSource)) {
                    return multibranchProject;
                }
                return jenkins.jenkins;
            }
        };
    }
}
