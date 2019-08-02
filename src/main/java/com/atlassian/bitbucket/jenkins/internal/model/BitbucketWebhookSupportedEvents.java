package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebhookSupportedEvents {

    private Set<String> applicationWebHooks;

    @JsonCreator
    public BitbucketWebhookSupportedEvents(@JsonProperty(value = "application-webhooks") Set<String> applicationWebHooks) {
        this.applicationWebHooks = applicationWebHooks;
    }

    public Set<String> getApplicationWebHooks() {
        return applicationWebHooks;
    }
}
