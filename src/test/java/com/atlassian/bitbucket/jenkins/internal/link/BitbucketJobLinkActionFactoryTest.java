package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
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
    private WorkflowJob workflowJob;
    private WorkflowJob multibranchJob;
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
        multibranchJob = jenkins.createProject(WorkflowJob.class, "branch1");
        multibranchJobFromSource = jenkins.createProject(WorkflowJob.class, "branch2");

        when(freeStyleProject.getScm()).thenReturn(scm);
        when(multibranchProject.getSCMSources()).thenReturn(Arrays.asList(mockSCMSource));

        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(configuration));
        when(configuration.getBaseUrl()).thenReturn(BASE_URL);
        when(configuration.validate()).thenReturn(FormValidation.ok());

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
    public void testCreateMultibranchSource() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchJobFromSource);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo/compare/commits?sourceBranch=refs%2Fheads%2Fbranch2"));
    }

    @Test
    public void testCreateMultibranchCustomStep() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchJob);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo/compare/commits?sourceBranch=refs%2Fheads%2Fbranch1"));
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
        return new BitbucketJobLinkActionFactory(externalLinkUtils) {

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
