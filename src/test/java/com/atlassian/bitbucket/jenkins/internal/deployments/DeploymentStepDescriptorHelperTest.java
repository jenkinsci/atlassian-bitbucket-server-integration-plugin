package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentStepDescriptorHelperTest {

    private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();
    @InjectMocks
    DeploymentStepDescriptorHelper helper;
    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Jenkins jenkins;

    @Before
    public void setup() throws Exception {
        when(jenkinsProvider.get()).thenReturn(jenkins);
    }

    @Test
    public void testDoCheckEnvironmentKeyBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentKey(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentKeyTooLong() {
        Item context = mock(Item.class);
        String environmentKey = StringUtils.repeat("a", 256);

        FormValidation formValidation = helper.doCheckEnvironmentKey(context, environmentKey);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment key must be shorter than 255 characters."));
    }

    @Test
    public void testDoCheckEnvironmentKey() {
        Item context = mock(Item.class);
        String environmentKey = "my key";

        FormValidation formValidation = helper.doCheckEnvironmentKey(context, environmentKey);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentNameBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentName(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment name is required."));
    }

    @Test
    public void testDoCheckEnvironmentNameContextNull() {
        String environmentName = "my env";

        FormValidation formValidation = helper.doCheckEnvironmentName(null, environmentName);

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentNameTooLong() {
        Item context = mock(Item.class);
        String environmentName = StringUtils.repeat("a", 256);

        FormValidation formValidation = helper.doCheckEnvironmentName(context, environmentName);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment name must be shorter than 255 characters."));
    }

    @Test
    public void testDoCheckEnvironmentName() {
        Item context = mock(Item.class);
        String environmentName = "my env";

        FormValidation formValidation = helper.doCheckEnvironmentName(context, environmentName);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentTypeBadEnvironmentType() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentType(context, "not an environment type");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment type should be one of DEVELOPMENT, PRODUCTION, STAGING, TESTING."));
    }

    @Test
    public void testDoCheckEnvironmentTypeBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentType(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentTypeContextNull() {
        FormValidation formValidation = helper.doCheckEnvironmentType(null, "Production");

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentType() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentType(context, "PRODUCTION");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentUrlBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentUrl(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentUrlContextNull() {
        FormValidation formValidation = helper.doCheckEnvironmentUrl(null, "http://my-env");

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoCheckEnvironmentUrlInvalid() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentUrl(context, "not a url");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment URL must be a valid URL."));
    }

    @Test
    public void testDoCheckEnvironmentUrlNotAbsolute() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentUrl(context, "/relative/url");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The deployment URI must be absolute."));
    }

    @Test
    public void testDoCheckEnvironmentUrlTooLong() {
        Item context = mock(Item.class);
        String environmentUrl = format("http://%s", StringUtils.repeat("a", 1018));

        FormValidation formValidation = helper.doCheckEnvironmentUrl(context, environmentUrl);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The deployment URI must be shorter than 1024 characters."));
    }

    @Test
    public void testDoCheckEnvironmentUrl() {
        Item context = mock(Item.class);

        FormValidation formValidation = helper.doCheckEnvironmentUrl(context, "http://my-env");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDoFillEnvironmentTypeItems() {
        ListBoxModel options = helper.doFillEnvironmentTypeItems(null);

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(options, hasSize(5));
        assertThat(options.get(0).name, equalTo("- none -"));
        assertThat(options.get(0).value, equalTo(""));
        assertThat(options.get(1).name, equalTo("Production"));
        assertThat(options.get(1).value, equalTo("PRODUCTION"));
        assertThat(options.get(2).name, equalTo("Staging"));
        assertThat(options.get(2).value, equalTo("STAGING"));
        assertThat(options.get(3).name, equalTo("Testing"));
        assertThat(options.get(3).value, equalTo("TESTING"));
        assertThat(options.get(4).name, equalTo("Development"));
        assertThat(options.get(4).value, equalTo("DEVELOPMENT"));
    }
}