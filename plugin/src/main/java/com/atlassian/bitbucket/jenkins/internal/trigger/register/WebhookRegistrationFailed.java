package com.atlassian.bitbucket.jenkins.internal.trigger.register;

/**
 * An exception raised when the plugin can't add webhook to bitbucket.
 */
public class WebhookRegistrationFailed extends RuntimeException {

    public WebhookRegistrationFailed(String message) {
        super(message);
    }

    public WebhookRegistrationFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
