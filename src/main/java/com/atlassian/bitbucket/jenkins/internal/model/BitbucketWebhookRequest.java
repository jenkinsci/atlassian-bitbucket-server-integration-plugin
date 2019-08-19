package com.atlassian.bitbucket.jenkins.internal.model;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;

public class BitbucketWebhookRequest {

    private final String name;
    private final Set<String> events;
    private final String url;
    private final boolean isActive;

    protected BitbucketWebhookRequest(BitbucketWebhookRequestBuilder builder) {
        this.name = builder.name;
        this.events = builder.events;
        this.url = builder.url;
        this.isActive = builder.isActive;
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
    public static class BitbucketWebhookRequestBuilder {

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
            requireNonNull(events, "Specify the web hook events");
            requireNonNull(url, "Specify the Call back URL");
            requireNonNull(name, "Specify the name of the webhook.");
            return new BitbucketWebhookRequest(this);
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
