package com.atlassian.bitbucket.jenkins.internal.client.exception;

public class WebhookNotSupportedException extends RuntimeException {

    public WebhookNotSupportedException(String message) {
        super(message);
    }
}
