package com.atlassian.bitbucket.jenkins.internal.trigger.register;

public interface WebhookHandler {

    WebhookRegisterResult register(WebhookRegisterRequest request);
}
