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

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPluginConfigurationTest {

    private static final String ERROR_MESSAGE = "ERROR";
    private static final String WARNING_MESSAGE = "WARNING";
    private final JSONObject formData = new JSONObject();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    private BitbucketServerConfiguration multipleInvalidServerConfiguration;
    @InjectMocks
    private BitbucketPluginConfiguration pluginConfiguration = new BitbucketPluginConfiguration() {
        public synchronized void load() {
            //overridden to ease unit testing
        }

        public synchronized void save() {
            //overridden to ease unit testing
        }
    };
    @Mock
    private StaplerRequest request;
    @Mock
    private BitbucketServerConfiguration singleInvalidServerConfiguration;
    @Mock
    private BitbucketServerConfiguration validServerConfiguration;

    @Before
    public void setup() {
        when(validServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.ok(), FormValidation.ok()
        ));
        when(singleInvalidServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.warning(WARNING_MESSAGE), FormValidation.error(ERROR_MESSAGE)
        ));
        when(multipleInvalidServerConfiguration.validate()).thenReturn(Arrays.asList(
                FormValidation.ok(), FormValidation.error(ERROR_MESSAGE), FormValidation.error(ERROR_MESSAGE)
        ));
    }

    @Test
    public void testConfigureMultipleInvalid() throws Descriptor.FormException {
        expectedException.expect(Descriptor.FormException.class);
        expectedException.expectMessage(
                BitbucketServerConfiguration.MULTIPLE_ERRORS_MESSAGE + BitbucketPluginConfiguration.FORM_INVALID_C2A);

        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, multipleInvalidServerConfiguration));
        pluginConfiguration.configure(request, formData);
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureSingleInvalid() throws Descriptor.FormException {
        expectedException.expect(Descriptor.FormException.class);
        expectedException.expectMessage(ERROR_MESSAGE + BitbucketPluginConfiguration.FORM_INVALID_C2A);

        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration, singleInvalidServerConfiguration));
        pluginConfiguration.configure(request, formData);
        verify(request).bindJSON(pluginConfiguration, formData);
    }

    @Test
    public void testConfigureValid() throws Descriptor.FormException {
        pluginConfiguration.setServerList(Arrays.asList(validServerConfiguration));
        assertEquals(pluginConfiguration.configure(request, formData), true);
        verify(request).bindJSON(pluginConfiguration, formData);
    }
}