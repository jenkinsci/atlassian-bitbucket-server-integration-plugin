package com.atlassian.bitbucket.jenkins.internal.client.exception;

/**
 * Caller requested a result but the server did not reply with a body.
 */
public class NoContentException extends BitbucketClientException {

    public NoContentException(String message, int responseCode) {
        super(message, responseCode, null);
    }
}
