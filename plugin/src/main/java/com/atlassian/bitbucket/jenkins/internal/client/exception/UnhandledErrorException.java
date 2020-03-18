package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nullable;

/**
 * Thrown when the server replies with a response code the client can't handle.
 */
public class UnhandledErrorException extends BitbucketClientException {

    public UnhandledErrorException(String message, int responseCode, @Nullable String body) {
        super(message, responseCode, body);
    }
}
