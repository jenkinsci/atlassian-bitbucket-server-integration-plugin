package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.model.Action;
import hudson.model.Project;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketMultiBranchLinkActionFactoryTest {

    private static final String SERVER_ID = "Test-Server-ID";
    private static final String CREDENTIALS_ID = "Test-Credentials-ID";
    private static final String PROJECT_NAME = "Test-Project-Name";
    private static final String REPOSITORY_NAME = "Test-Repository-Name";
    private static final String BASE_URL = "http://localhost:8080/bitbucket";
    private final BitbucketMultiBranchLinkActionFactory actionFactory = new BitbucketMultiBranchLinkActionFactory();

    @Mock
    private BitbucketSCMSource bitbucketSCMSource;
    @Mock
    private BitbucketServerConfiguration configuration;
    @Mock
    private BitbucketSCMSource.DescriptorImpl descriptor;
    @Mock
    private WorkflowMultiBranchProject multiBranchProject;
    @Mock
    private Project target;

    @Before
    public void init() {
        when(multiBranchProject.getSCMSources()).thenReturn(Collections.singletonList(bitbucketSCMSource));

        when(bitbucketSCMSource.getServerId()).thenReturn(SERVER_ID);
        when(bitbucketSCMSource.getCredentialsId()).thenReturn(CREDENTIALS_ID);
        when(bitbucketSCMSource.getProjectName()).thenReturn(PROJECT_NAME);
        when(bitbucketSCMSource.getRepositoryName()).thenReturn(REPOSITORY_NAME);
        when(bitbucketSCMSource.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getConfiguration(SERVER_ID)).thenReturn(Optional.of(configuration));
        when(descriptor.doCheckProjectName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME)).thenReturn(FormValidation.ok());
        when(descriptor.doCheckRepositoryName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME, REPOSITORY_NAME)).thenReturn(FormValidation.ok());
        when(configuration.getBaseUrl()).thenReturn(BASE_URL);
        when(configuration.validate()).thenReturn(FormValidation.ok());
    }

    @Test
    public void testCreate() {
        when(bitbucketSCMSource.getProjectKey()).thenReturn("PROJ");
        when(bitbucketSCMSource.getRepositorySlug()).thenReturn("repo");
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateNotBitbucketSCMFreestyle() {
        when(multiBranchProject.getSCMSources()).thenReturn(Collections.singletonList(mock(SCMSource.class)));
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateProjectNameInvalid() {
        when(descriptor.doCheckProjectName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME))
                .thenReturn(FormValidation.error("Bad project name"));
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateRepoNameInvalid() {
        when(descriptor.doCheckRepositoryName(target, SERVER_ID, CREDENTIALS_ID, PROJECT_NAME, REPOSITORY_NAME))
                .thenReturn(FormValidation.error("Bad repository name"));
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerConfigurationInvalid() {
        when(configuration.validate()).thenReturn(FormValidation.error("config invalid"));
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerNotConfigured() {
        when(descriptor.getConfiguration(SERVER_ID)).thenReturn(Optional.empty());
        Collection<? extends Action> actions = actionFactory.createFor(multiBranchProject);

        assertThat(actions.size(), equalTo(0));
    }
}