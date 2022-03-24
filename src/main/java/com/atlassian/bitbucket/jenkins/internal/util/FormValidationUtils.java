package com.atlassian.bitbucket.jenkins.internal.util;

import hudson.util.FormValidation;

import java.net.MalformedURLException;
import java.net.URL;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public final class FormValidationUtils {

    /**
     * Validates that the provided baseUrl value is syntactically valid
     *
     * @param baseUrl the URL to check
     * @return FormValidation with Kind.ok if syntactically valid; Kind.error otherwise
     */
    public static FormValidation checkBaseUrl(String baseUrl) {
        if (isEmpty(baseUrl)) {
            return FormValidation.error("You must enter a valid HTTP URL");
        }
        try {
            URL base = new URL(baseUrl);
            if (isBlank(base.getHost())) {
                return FormValidation.error(
                        "This isn't a valid URL. Check for typos and make sure to include http:// or https://");
            } else if (base.getHost().endsWith("bitbucket.org")) {
                return FormValidation.error("This plugin does not support connecting to bitbucket.org. It is for Bitbucket Server instances only.");
            }
        } catch (MalformedURLException e) {
            return FormValidation.error(
                    "This isn't a valid URL. Check for typos and make sure to include http:// or https://");
        }
        return FormValidation.ok();
    }
}
