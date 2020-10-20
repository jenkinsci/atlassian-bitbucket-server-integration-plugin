package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketJobLinkActionFactoryTest {

    private static final String SERVER_ID = "Test-Server-ID";
    private static final String CREDENTIALS_ID = "Test-Credentials-ID";
    private static final String PROJECT_NAME = "Test-Project-Name";
    private static final String REPOSITORY_NAME = "Test-Repository-Name";
    private static final String BASE_URL = "http://localhost:8080/bitbucket";
    private final BitbucketJobLinkActionFactory actionFactory = new BitbucketJobLinkActionFactory();
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BitbucketServerConfiguration configuration;
    @Mock
    private BitbucketSCM.DescriptorImpl descriptor;
    @Mock
    private FreeStyleProject freeStyleProject;
    private WorkflowJob workflowJob;
    @Mock
    private Project target;

    @Before
    public void init() throws IOException {
        workflowJob = jenkins.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsScmFlowDefinition(bitbucketSCM, "Jenkinsfile"));

        when(freeStyleProject.getScm()).thenReturn(bitbucketSCM);
        when(bitbucketSCM.getServerId()).thenReturn(SERVER_ID);
        when(bitbucketSCM.getCredentialsId()).thenReturn(CREDENTIALS_ID);
        when(bitbucketSCM.getProjectName()).thenReturn(PROJECT_NAME);
        when(bitbucketSCM.getRepositoryName()).thenReturn(REPOSITORY_NAME);
        when(bitbucketSCM.getDescriptor()).thenReturn((SCMDescriptor) descriptor);
        when(descriptor.getConfiguration(SERVER_ID)).thenReturn(Optional.of(configuration));
        when(descriptor.doCheckProjectName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME)).thenReturn(FormValidation.ok());
        when(descriptor.doCheckRepositoryName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME, REPOSITORY_NAME)).thenReturn(FormValidation.ok());
        when(configuration.getBaseUrl()).thenReturn(BASE_URL);
        when(configuration.validate()).thenReturn(FormValidation.ok());
    }

    @Test
    public void testCreateFreestyle() {
        when(bitbucketSCM.getProjectKey()).thenReturn("PROJ");
        when(bitbucketSCM.getRepositorySlug()).thenReturn("repo");
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateWorkflow() {
        when(bitbucketSCM.getProjectKey()).thenReturn("PROJ");
        when(bitbucketSCM.getRepositorySlug()).thenReturn("repo");
        Collection<? extends Action> actions = actionFactory.createFor(workflowJob);

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
        when(descriptor.doCheckProjectName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME))
                .thenReturn(FormValidation.error("Bad project name"));
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateRepoNameInvalid() {
        when(descriptor.doCheckRepositoryName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME, REPOSITORY_NAME))
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
        when(descriptor.getConfiguration(SERVER_ID)).thenReturn(Optional.empty());
        Collection<? extends Action> actions = actionFactory.createFor(freeStyleProject);

        assertThat(actions.size(), equalTo(0));
    }
}