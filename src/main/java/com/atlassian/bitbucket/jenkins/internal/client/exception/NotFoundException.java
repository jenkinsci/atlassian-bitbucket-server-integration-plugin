package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nullable;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * The requested URL does not exist on the server.
 */
public class NotFoundException extends BitbucketClientException {

    public NotFoundException(String message, @Nullable String body) {
        super(message, HTTP_NOT_FOUND, body);
    }
}
