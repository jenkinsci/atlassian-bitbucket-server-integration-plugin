package com.atlassian.bitbucket.jenkins.internal.link;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.model.Action;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketMultibranchLinkActionFactoryTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    private BitbucketMultibranchLinkActionFactory actionFactory;

    private static final String SERVER_ID = "Test-Server-ID";
    private static final String BASE_URL = "http://localhost:8080/bitbucket";

    private BitbucketExternalLinkUtils externalLinkUtils;
    @Mock
    private BitbucketSCMRepository bitbucketRepository;
    @Mock
    private BitbucketSCMSource scmSource;
    @Mock
    private BitbucketServerConfiguration configuration;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private WorkflowMultiBranchProject multibranchProject;

    @Before
    public void setup() {
        doReturn(Arrays.asList(scmSource, mock(SCMSource.class))).when(multibranchProject).getSCMSources();
        doReturn(bitbucketRepository).when(scmSource).getBitbucketSCMRepository();

        doReturn("PROJ").when(bitbucketRepository).getProjectKey();
        doReturn("repo").when(bitbucketRepository).getRepositorySlug();
        doReturn(SERVER_ID).when(bitbucketRepository).getServerId();

        doReturn(Optional.of(configuration)).when(pluginConfiguration).getServerById(SERVER_ID);
        doReturn(BASE_URL).when(configuration).getBaseUrl();
        doReturn(FormValidation.ok()).when(configuration).validate();

        externalLinkUtils = new BitbucketExternalLinkUtils(pluginConfiguration);
        actionFactory = new BitbucketMultibranchLinkActionFactory(externalLinkUtils);
    }

    @Test
    public void testCreate() {
        Collection<? extends Action> actions = actionFactory.createFor(multibranchProject);

        assertThat(actions.size(), equalTo(1));
        BitbucketExternalLink externalLink = (BitbucketExternalLink) actions.stream().findFirst().get();
        assertThat(externalLink.getUrlName(), equalTo(BASE_URL + "/projects/PROJ/repos/repo"));
    }

    @Test
    public void testCreateNotBitbucketSCM() {
        doReturn(Collections.singletonList(mock(SCMSource.class))).when(multibranchProject).getSCMSources();
        Collection<? extends Action> actions = actionFactory.createFor(multibranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerConfigurationInvalid() {
        when(configuration.validate()).thenReturn(FormValidation.error("config invalid"));
        Collection<? extends Action> actions = actionFactory.createFor(multibranchProject);

        assertThat(actions.size(), equalTo(0));
    }

    @Test
    public void testCreateServerNotConfigured() {
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.empty());
        Collection<? extends Action> actions = actionFactory.createFor(multibranchProject);

        assertThat(actions.size(), equalTo(0));
    }
}
