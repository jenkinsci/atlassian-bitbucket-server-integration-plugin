package com.atlassian.bitbucket.jenkins.internal.client.exception;

import java.io.IOException;

/**
 * Could not connect to the server, either the socket was rejected, or the connection timed out.
 */
public class ConnectionFailureException extends BitbucketClientException {

    public ConnectionFailureException(IOException e) {
        super(e);
    }
}
