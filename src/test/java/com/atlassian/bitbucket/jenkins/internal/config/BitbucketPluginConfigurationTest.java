package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPluginConfigurationTest {

    private static final String ERROR_MESSAGE = "ERROR";
    @ClassRule
    public static final JenkinsRule jenkins = new JenkinsRule();
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
        when(invalidServerConfigurationOne.validate()).thenReturn(FormValidation.error(ERROR_MESSAGE));
        when(invalidServerConfigurationTwo.validate()).thenReturn(FormValidation.error(ERROR_MESSAGE));
    }

    @Test
    public void testConfigureMultipleInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        assertFalse(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureSingleInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne));
        assertFalse(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureValid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration));
        assertTrue(pluginConfiguration.configure(request, formData));
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testGetValidServerListEmpty() {
        pluginConfiguration.setServerList(new ArrayList<>());
        assertThat(pluginConfiguration.getValidServerList(), Matchers.hasSize(0));
    }

    @Test
    public void testGetValidServerListAllValid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration));
        List<BitbucketServerConfiguration> validServerList = pluginConfiguration.getValidServerList();
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
    }

    @Test
    public void testGetValidServerListSomeInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        List<BitbucketServerConfiguration> validServerList = pluginConfiguration.getValidServerList();
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
        assertThat(validServerList, Matchers.contains(validServerConfiguration));
    }

    @Test
    public void testHasAnyInvalidConfigurationEmpty() {
        pluginConfiguration.setServerList(new ArrayList<>());
        assertFalse(pluginConfiguration.hasAnyInvalidConfiguration());
    }

    @Test
    public void testHasAnyInvalidConfigurationNoInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration));
        assertFalse(pluginConfiguration.hasAnyInvalidConfiguration());
    }

    @Test
    public void testHasAnyInvalidConfigurationSomeInvalid() {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, invalidServerConfigurationOne, invalidServerConfigurationTwo));
        assertTrue(pluginConfiguration.hasAnyInvalidConfiguration());
    }
}