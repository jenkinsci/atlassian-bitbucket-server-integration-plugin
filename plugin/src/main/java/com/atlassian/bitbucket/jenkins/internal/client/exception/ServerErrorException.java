package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nullable;

/**
 * The server responded with a 500 family exception.
 */
public class ServerErrorException extends BitbucketClientException {

    public ServerErrorException(String message, int responseCode, @Nullable String body) {
        super(message, responseCode, body);
    }
}
