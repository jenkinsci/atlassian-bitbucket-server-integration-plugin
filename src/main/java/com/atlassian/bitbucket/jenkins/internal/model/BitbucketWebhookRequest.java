package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class BitbucketWebhookRequest {

    private final String name;
    private final Set<String> events;
    private final String url;
    private final boolean isActive;

    @JsonCreator
    public BitbucketWebhookRequest(@JsonProperty(value = "name") String name,
                                   @JsonProperty(value = "events") Set<String> events,
                                   @JsonProperty(value = "url") String url,
                                   @JsonProperty(value = "active") boolean isActive) {
        this.name = name;
        this.events = events;
        this.url = url;
        this.isActive = isActive;
    }

    public String getName() {
        return name;
    }

    public Set<String> getEvents() {
        return events;
    }

    public String getUrl() {
        return url;
    }

    public boolean isActive() {
        return isActive;
    }
}
