package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;

/**
 * Represents a webhook register request.
 */
public class WebhookRegisterRequest {

    private final Set<String> events;
    private final String url;
    private final String name;

    private WebhookRegisterRequest(WebhookRegisterRequestBuilder builder) {
        events = unmodifiableSet(builder.events);
        url = builder.url;
        name = builder.name;
    }

    public BitbucketWebhookRequest getRequestPayload() {
        return new BitbucketWebhookRequest(name, events, url, true);
    }

    /**
     * A builder to provide fluent way of building webhook register request. 
     */
    public static class WebhookRegisterRequestBuilder {

        private final Set<String> events;
        private String name;
        private String url;

        private WebhookRegisterRequestBuilder(Set<String> events) {
            this.events = events;
        }

        public static WebhookRegisterRequestBuilder aRequestFor(String event, String... events) {
            Set<String> eventSet = new LinkedHashSet<>();
            eventSet.add(event);
            eventSet.addAll(asList(events));
            return new WebhookRegisterRequestBuilder(eventSet);
        }

        public WebhookRegisterRequest build() {
            requireNonNull(events, "Specify the web hook events");
            requireNonNull(url, "Specify the Call back URL");
            requireNonNull(name, "Specify the name of the webhook.");
            return new WebhookRegisterRequest(this);
        }

        public WebhookRegisterRequestBuilder name(String name) {
            this.name = name;
            return this;
        }

        public WebhookRegisterRequestBuilder withCallbackTo(String url) {
            this.url = url;
            return this;
        }
    }
}
