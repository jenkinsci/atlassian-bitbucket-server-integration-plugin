package com.atlassian.bitbucket.jenkins.internal.client.exception;

/**
 * Throws when the remote bitbucket server does not return webhook as one of its capabilities. We expect the minimum
 * version of Bitbucket that this plugin support have webhook capability.
 */
public class WebhookNotSupportedException extends RuntimeException {

    public WebhookNotSupportedException(String message) {
        super(message);
    }
}
