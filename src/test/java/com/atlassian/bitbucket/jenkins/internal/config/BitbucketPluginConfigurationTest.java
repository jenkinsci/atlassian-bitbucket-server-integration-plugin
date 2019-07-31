package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.model.Descriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPluginConfigurationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private String errorMessage = "ERROR";
    private JSONObject formData = new JSONObject();
    @Mock
    private BitbucketServerConfiguration multipleInvalidServerConfiguration;
    @InjectMocks
    private BitbucketPluginConfiguration pluginConfiguration = new BitbucketPluginConfiguration();
    @Mock
    private StaplerRequest request;
    @Mock
    private BitbucketServerConfiguration singleInvalidServerConfiguration;
    @Mock
    private BitbucketServerConfiguration validServerConfiguration;
    private String warningMessage = "WARNING";

    @Before
    public void setup() {
        when(validServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.ok(), FormValidation.ok()
        ));
        when(singleInvalidServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.warning(warningMessage), FormValidation.error(errorMessage)
        ));
        when(multipleInvalidServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.error(errorMessage), FormValidation.error(errorMessage)
        ));
    }

    @Test
    public void testConfigureMultipleInvalid() throws Descriptor.FormException {
        expectedException.expect(Descriptor.FormException.class);
        expectedException.expectMessage(BitbucketServerConfiguration.MULTIPLE_ERRORS_MESSAGE);
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, multipleInvalidServerConfiguration));
        pluginConfiguration.configure(request, formData);
    }

    @Test
    public void testConfigureSingleInvalid() throws Descriptor.FormException {
        expectedException.expect(Descriptor.FormException.class);
        expectedException.expectMessage(errorMessage);
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, singleInvalidServerConfiguration));
        pluginConfiguration.configure(request, formData);
    }

    @Test
    public void testConfigureValid() throws Descriptor.FormException {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration));
        assertEquals(pluginConfiguration.configure(request, formData), true);
    }
}