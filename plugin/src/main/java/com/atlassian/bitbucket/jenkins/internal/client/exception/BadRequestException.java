package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nullable;

/**
 * Thrown when the server rejected the request with a 400 (but <em>NOT</em> a 404) family response.
 */
public class BadRequestException extends BitbucketClientException {

    public BadRequestException(String message, int responseCode, @Nullable String body) {
        super(message, responseCode, body);
    }
}
