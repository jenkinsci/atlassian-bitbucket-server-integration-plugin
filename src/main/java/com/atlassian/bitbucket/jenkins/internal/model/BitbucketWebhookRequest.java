package com.atlassian.bitbucket.jenkins.internal.model;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public class BitbucketWebhookRequest {

    private final String name;
    private final Set<String> events;
    private final String url;
    private final boolean isActive;

    protected BitbucketWebhookRequest(String name, Set<String> events, String url, boolean isActive) {
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

    /**
     * A builder to provide fluent way of building webhook register request.
     */
    public static final class BitbucketWebhookRequestBuilder {

        private final Set<String> events;
        private String name;
        private String url;
        private boolean isActive = true;

        private BitbucketWebhookRequestBuilder(Set<String> events) {
            this.events = events;
        }

        public static BitbucketWebhookRequestBuilder aRequestFor(String event, String... events) {
            Set<String> eventSet = new LinkedHashSet<>();
            eventSet.add(event);
            eventSet.addAll(asList(events));
            return aRequestFor(eventSet);
        }

        static BitbucketWebhookRequestBuilder aRequestFor(Set<String> eventSet) {
            return new BitbucketWebhookRequestBuilder(eventSet);
        }

        public BitbucketWebhookRequest build() {
            requireNonNull(events, "Specify the webhook events");
            requireNonNull(url, "Specify the Call back URL");
            requireNonNull(name, "Specify the name of the webhook.");
            return new BitbucketWebhookRequest(name, events, url, isActive);
        }

        public BitbucketWebhookRequestBuilder withIsActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public BitbucketWebhookRequestBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BitbucketWebhookRequestBuilder withCallbackTo(String url) {
            this.url = url;
            return this;
        }
    }
}
