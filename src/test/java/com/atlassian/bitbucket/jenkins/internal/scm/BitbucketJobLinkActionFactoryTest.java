package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.scm.SCM;
import hudson.util.FormValidation;
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
    private static final String CREDENTIALS_ID = "Test-Credentials-ID";
    private static final String PROJECT_NAME = "Test-Project-Name";
    private static final String REPOSITORY_NAME = "Test-Repository-Name";
    private static final String BASE_URL = "http://localhost:8080/bitbucket";

    private BitbucketJobLinkActionFactory actionFactory;
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    @Mock
    BitbucketSCM scm;
    @Mock
    private BitbucketSCMRepository bitbucketRepository;
    @Mock
    private BitbucketServerConfiguration configuration;
    @Mock
    private BitbucketScmFormValidationDelegate formValidationDelegate;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private FreeStyleProject freeStyleProject;
    private WorkflowJob workflowJob;
    private WorkflowJob multibranchJob;
    private WorkflowJob multibranchJobFromSource;
    @Mock
    private WorkflowMultiBranchProject multibranchProject;
    @Mock
    private Project target;

    @Before
    public void init() throws IOException {
        when(scm.getBitbucketSCMRepository()).thenReturn(bitbucketRepository);
        BitbucketSCMSource mockSCMSource = mock(BitbucketSCMSource.class);
        when(mockSCMSource.getBitbucketSCMRepository()).thenReturn(bitbucketRepository);
        when(bitbucketRepository.getProjectKey()).thenReturn("PROJ");
        when(bitbucketRepository.getRepositorySlug()).thenReturn("repo");

        workflowJob = jenkins.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));
        multibranchJob = jenkins.createProject(WorkflowJob.class);
        multibranchJobFromSource = jenkins.createProject(WorkflowJob.class);

        when(freeStyleProject.getScm()).thenReturn(scm);
        when(multibranchProject.getSCMSources()).thenReturn(Arrays.asList(mockSCMSource));
        when(bitbucketRepository.getServerId()).thenReturn(SERVER_ID);
        when(bitbucketRepository.getCredentialsId()).thenReturn(CREDENTIALS_ID);
        when(bitbucketRepository.getProjectName()).thenReturn(PROJECT_NAME);
        when(bitbucketRepository.getRepositoryName()).thenReturn(REPOSITORY_NAME);

        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(configuration));
        when(formValidationDelegate.doCheckProjectName(any(), eq(SERVER_ID), eq(CREDENTIALS_ID), eq(PROJECT_NAME))).thenReturn(FormValidation.ok());
        when(formValidationDelegate.doCheckRepositoryName(any(), eq(SERVER_ID), eq(CREDENTIALS_ID), eq(PROJECT_NAME), eq(REPOSITORY_NAME))).thenReturn(FormValidation.ok());
        when(configuration.getBaseUrl()).thenReturn(BASE_URL);
        when(configuration.validate()).thenReturn(FormValidation.ok());

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
    public void testCreateMultibranchSource() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchJobFromSource);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateMultibranchCustomStep() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchJob);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
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
    public void testCreateProjectNameInvalid() {
        when(formValidationDelegate.doCheckProjectName(any(), eq(SERVER_ID), eq(CREDENTIALS_ID), eq(PROJECT_NAME)))
                .thenReturn(FormValidation.error("Bad project name"));
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateRepoNameInvalid() {
        when(formValidationDelegate.doCheckRepositoryName(any(), eq(SERVER_ID), eq(CREDENTIALS_ID), eq(PROJECT_NAME), eq(REPOSITORY_NAME)))
                .thenReturn(FormValidation.error("Bad repository name"));
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

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
        return new BitbucketJobLinkActionFactory(pluginConfiguration, formValidationDelegate) {

            @Override
            Collection<? extends SCM> getWorkflowSCMs(WorkflowJob job) {
                if (Objects.equals(job, multibranchJob)) {
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