package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

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
}