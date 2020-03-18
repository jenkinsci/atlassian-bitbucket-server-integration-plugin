package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebhookSupportedEvents {

    private final Set<String> applicationWebHooks;

    @JsonCreator
    public BitbucketWebhookSupportedEvents(@JsonProperty(value = "application-webhooks") Set<String> applicationWebHooks) {
        this.applicationWebHooks = unmodifiableSet(requireNonNull(applicationWebHooks, "Application hooks events unavailable"));
    }

    public Set<String> getApplicationWebHooks() {
        return applicationWebHooks;
    }
}
