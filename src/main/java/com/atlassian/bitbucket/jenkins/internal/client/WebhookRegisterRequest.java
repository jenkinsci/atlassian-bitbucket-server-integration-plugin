package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.google.common.collect.Sets;

import java.util.Set;

import static java.util.Objects.requireNonNull;

public class WebhookRegisterRequest {

    private final String projectSlug;
    private final String repoSlug;
    private final Set<String> events;
    private final String url;
    private final String name;

    private WebhookRegisterRequest(WebhookRegisterRequestBuilder builder) {
        projectSlug = builder.projectSlug;
        repoSlug = builder.repoSlug;
        events = builder.events;
        url = builder.url;
        name = builder.name;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getRepoSlug() {
        return repoSlug;
    }

    public BitbucketWebhookRequest getRequestPayload() {
        return new BitbucketWebhookRequest(name, events, url, true);
    }

    public static class WebhookRegisterRequestBuilder {

        private final Set<String> events;
        private String url;
        private String projectSlug;
        private String repoSlug;
        private String name;

        private WebhookRegisterRequestBuilder(Set<String> events) {
            this.events = events;
        }

        public static WebhookRegisterRequestBuilder aRequestFor(String event, String... events) {
            Set<String> eventSet = Sets.newHashSet(events);
            eventSet.add(event);
            return new WebhookRegisterRequestBuilder(eventSet);
        }

        public WebhookRegisterRequest build() {
            requireNonNull(projectSlug, "Specify the project slug");
            requireNonNull(repoSlug, "Specify the repository slug");
            requireNonNull(url, "Specify the Call back URL");
            requireNonNull(name, "Specify the name of the webhook.");
            return new WebhookRegisterRequest(this);
        }

        public WebhookRegisterRequestBuilder onProject(String projectSlug) {
            this.projectSlug = projectSlug;
            return this;
        }

        public WebhookRegisterRequestBuilder onRepo(String repoSlug) {
            this.repoSlug = repoSlug;
            return this;
        }

        public WebhookRegisterRequestBuilder withCallbackTo(String url) {
            this.url = url;
            return this;
        }

        public WebhookRegisterRequestBuilder name(String name) {
            this.name = name;
            return this;
        }
    }
}
