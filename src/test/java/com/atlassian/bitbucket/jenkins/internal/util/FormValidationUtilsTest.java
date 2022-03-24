package com.atlassian.bitbucket.jenkins.internal.util;

import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FormValidationUtilsTest {

    private static final String URL_BITBUCKET_ORG_ERROR =
            "This plugin does not support connecting to bitbucket.org. It is for Bitbucket Server instances only.";
    private static final String URL_ERROR_MESSAGE =
            "This isn&#039;t a valid URL. Check for typos and make sure to include http:// or https://";
    private static final String URL_REQUIRED_ERROR = "You must enter a valid HTTP URL";

    @Test
    public void testDoCheckConsumerUrlBitbucketOrg() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("http://bitbucket.org");
        assertEquals(formValidation.kind, Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_BITBUCKET_ORG_ERROR);
    }

    @Test
    public void testDoCheckEmptyBaseUrlConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("http://");
        assertEquals(formValidation.kind, Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckEmptyConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("");
        assertEquals(formValidation.kind, Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_REQUIRED_ERROR);
    }

    @Test
    public void testDoCheckHttpConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("http://Callback/");
        assertEquals(formValidation.kind, Kind.OK);
    }

    @Test
    public void testDoCheckHttpsConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("https://Callback/");
        assertEquals(formValidation.kind, Kind.OK);
    }

    public void testDoCheckInvalidConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("Url1");
        assertEquals(formValidation.kind, Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckJavascriptInjectionConsumerUrl() {
        FormValidation formValidation = FormValidationUtils.checkBaseUrl("javascript:alert(1)");
        assertEquals(formValidation.kind, Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_ERROR_MESSAGE);
    }
}