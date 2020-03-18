package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.model.FreeStyleProject;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPluginConfigurationTest {

    @ClassRule
    public static final JenkinsRule jenkins = new JenkinsRule();
    private static final String ERROR_MESSAGE = "ERROR";
    private final JSONObject formData = new JSONObject();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    private BitbucketServerConfiguration invalidServerConfigurationOne;
    @Mock
    private BitbucketServerConfiguration invalidServerConfigurationTwo;
    @InjectMocks
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private StaplerRequest request;
    @Mock
    private BitbucketServerConfiguration validServerConfiguration;

    @Before
    public void setup() {
        pluginConfiguration = new BitbucketPluginConfiguration();
        when(validServerConfiguration.validate()).thenReturn(FormValidation.ok());
        when(validServerConfiguration.getId()).thenReturn("0");
        when(validServerConfiguration.getBaseUrl()).thenReturn("http://localhost:5990/bitbucket");
        when(invalidServerConfigurationOne.validate()).thenReturn(FormValidation.error(ERROR_MESSAGE));
        when(invalidServerConfigurationOne.getId()).thenReturn("1");
        when(invalidServerConfigurationOne.getBaseUrl()).thenReturn("http://localhost:6990/bitbucket");
        when(invalidServerConfigurationTwo.validate()).thenReturn(FormValidation.error(ERROR_MESSAGE));
        when(invalidServerConfigurationTwo.getId()).thenReturn("2");
        when(invalidServerConfigurationTwo.getBaseUrl()).thenReturn("http://localhost:7990/bitbucket");
    }

    @Test
    public void testConfigureChangedBaseUrlUpdatesJob() throws Exception {
        FreeStyleProject freeStyleProject = jenkins.createFreeStyleProject();
        BitbucketSCM bitbucketSCMInitial = mock(BitbucketSCM.class);
        when(bitbucketSCMInitial.getServerId()).thenReturn("0");
        freeStyleProject.setScm(bitbucketSCMInitial);
        assertThat(jenkins.getInstance().getAllItems(FreeStyleProject.class).get(0).getScm(),
                equalTo(bitbucketSCMInitial));

        WorkflowJob workflowJob = jenkins.createProject(WorkflowJob.class);
        workflowJob.setDefinition(new CpsScmFlowDefinition(bitbucketSCMInitial, "Jenkinsfile"));

        BitbucketServerConfiguration changedBaseUrlConfiguration = mock(BitbucketServerConfiguration.class);
        when(changedBaseUrlConfiguration.validate()).thenReturn(FormValidation.ok());
        when(changedBaseUrlConfiguration.getId()).thenReturn("0");
        when(changedBaseUrlConfiguration.getBaseUrl()).thenReturn("http://localhost:4990/bitbucket");
        pluginConfiguration.setServerList(singletonList(validServerConfiguration));
        formData.put("serverList", 1);
        doAnswer((Answer<Void>) invocation -> {
            pluginConfiguration.setServerList(singletonList(changedBaseUrlConfiguration));
            return null;
        }).when(request).bindJSON(pluginConfiguration, formData);

        assertTrue(pluginConfiguration.configure(request, formData));

        SCM newScm = jenkins.getInstance().getAllItems(FreeStyleProject.class).get(0).getScm();
        assertThat(newScm, not(equalTo(bitbucketSCMInitial)));
        assertThat(((BitbucketSCM) newScm).getServerId(), equalTo("0"));

        newScm = ((CpsScmFlowDefinition) jenkins.getInstance().getAllItems(WorkflowJob.class).get(0).getDefinition()).getScm();
        assertThat(newScm, not(equalTo(bitbucketSCMInitial)));
        assertThat(((BitbucketSCM) newScm).getServerId(), equalTo("0"));
    }

    @Test
    public void testConfigureMultipleInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        formData.put("serverList", 2);
        assertFalse(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureSingleInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne));
        formData.put("serverList", 1);
        assertFalse(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureValid() {
        pluginConfiguration.setServerList(singletonList(validServerConfiguration));
        formData.put("serverList", 1);
        assertTrue(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testGetValidServerListAllValid() {
        pluginConfiguration.setServerList(singletonList(validServerConfiguration));
        formData.put("serverList", 1);
        List<BitbucketServerConfiguration> validServerList = pluginConfiguration.getValidServerList();
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
    }

    @Test
    public void testGetValidServerListEmpty() {
        pluginConfiguration.setServerList(Collections.emptyList());
        assertThat(pluginConfiguration.getValidServerList(), Matchers.hasSize(0));
    }

    @Test
    public void testGetValidServerListSomeInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        formData.put("serverList", 2);
        List<BitbucketServerConfiguration> validServerList = pluginConfiguration.getValidServerList();
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
    }

    @Test
    public void testHasAnyInvalidConfigurationEmpty() {
        pluginConfiguration.setServerList(Collections.emptyList());
        assertFalse(pluginConfiguration.hasAnyInvalidConfiguration());
    }

    @Test
    public void testHasAnyInvalidConfigurationNoInvalid() {
        pluginConfiguration.setServerList(singletonList(validServerConfiguration));
        formData.put("serverList", 1);
        assertFalse(pluginConfiguration.hasAnyInvalidConfiguration());
    }

    @Test
    public void testHasAnyInvalidConfigurationSomeInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        formData.put("serverList", 2);
        assertTrue(pluginConfiguration.hasAnyInvalidConfiguration());
    }
}