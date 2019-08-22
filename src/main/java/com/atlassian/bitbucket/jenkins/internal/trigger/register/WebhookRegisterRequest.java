package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import static java.util.Objects.requireNonNull;

public class WebhookRegisterRequest {

    private final String name;
    private final String jenkinsUrl;
    private final boolean isMirror;

    private WebhookRegisterRequest(String name, String jenkinsUrl, boolean isMirror) {
        this.name = requireNonNull(name);
        this.jenkinsUrl = requireNonNull(jenkinsUrl);
        this.isMirror = isMirror;
    }

    public String getName() {
        return name;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public boolean isMirror() {
        return isMirror;
    }

    public static class WebhookRegisterRequestBuilder {

        private final String jenkinsUrl;
        private boolean isMirror;
        private String serverId;

        private WebhookRegisterRequestBuilder(String jenkinsUrl) {
            this.jenkinsUrl = jenkinsUrl;
        }

        public static WebhookRegisterRequestBuilder aRequestFor(String jenkinsUrl) {
            return new WebhookRegisterRequestBuilder(jenkinsUrl);
        }

        public WebhookRegisterRequest build() {
            return new WebhookRegisterRequest(serverId, jenkinsUrl, isMirror);
        }

        public WebhookRegisterRequestBuilder isMirror(boolean isMirror) {
            this.isMirror = isMirror;
            return this;
        }

        public WebhookRegisterRequestBuilder withName(String serverId) {
            this.serverId = serverId;
            return this;
        }
    }
}
