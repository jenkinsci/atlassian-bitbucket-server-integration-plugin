package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nullable;

/**
 * Thrown when the {@link com.cloudbees.plugins.credentials.Credentials} used did not have the
 * required permissions.
 */
public class AuthorizationException extends BitbucketClientException {

    public AuthorizationException(String message, int responseCode, @Nullable String body) {
        super(message, responseCode, body);
    }
}
