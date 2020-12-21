package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

import static java.util.Objects.requireNonNull;

public class BitbucketWebhookRequest {

    private final Map<String, String> configuration;
    private final String name;
    private final Set<String> events;
    private final String url;
    private final boolean isActive;

    @JsonCreator
    protected BitbucketWebhookRequest(@JsonProperty("name") String name,
                                      @JsonProperty("events") Set<String> events,
                                      @JsonProperty("url") String url,
                                      @JsonProperty("active") boolean isActive) {
        this.name = name;
        this.events = events;
        this.url = url;
        this.isActive = isActive;
        configuration = Collections.singletonMap("createdBy", "jenkins");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketWebhookRequest that = (BitbucketWebhookRequest) o;
        return isActive == that.isActive &&
               Objects.equals(configuration, that.configuration) &&
               Objects.equals(name, that.name) &&
               Objects.equals(events, that.events) &&
               Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configuration, name, events, url, isActive);
    }

    public Map<String, String> getConfiguration() {
        return configuration;
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

    /**
     * A builder to provide fluent way of building webhook register request.
     */
    public static final class Builder {

        private final Set<String> events;
        private String name;
        private String url;
        private boolean isActive = true;

        private Builder(Set<String> events) {
            this.events = events;
        }

        public static Builder aRequestFor(Collection<String> events) {
            Set<String> eventSet = new HashSet<>(events);
            return aRequestFor(eventSet);
        }

        static Builder aRequestFor(Set<String> eventSet) {
            return new Builder(eventSet);
        }

        public BitbucketWebhookRequest build() {
            requireNonNull(events, "Specify the webhook events");
            requireNonNull(url, "Specify the Call back URL");
            requireNonNull(name, "Specify the name of the webhook.");
            return new BitbucketWebhookRequest(name, events, url, isActive);
        }

        public Builder withIsActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder withCallbackTo(String url) {
            this.url = url;
            return this;
        }
    }
}
