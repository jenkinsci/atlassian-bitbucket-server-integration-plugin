package com.atlassian.bitbucket.jenkins.internal.trigger.register;

public class WebhookRegisterRequest {

    private final String jenkinsUrl;
    private final boolean isMirror;
    private final String serverId;

    public WebhookRegisterRequest(WebhookRegisterRequestBuilder builder) {
        jenkinsUrl = builder.jenkinsUrl;
        isMirror = builder.isMirror;
        serverId = builder.serverId;
    }

    public boolean isMirror() {
        return isMirror;
    }

    public String getServerId() {
        return serverId;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
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
            return new WebhookRegisterRequest(this);
        }

        public WebhookRegisterRequestBuilder isMirror(boolean isMirror) {
            this.isMirror = isMirror;
            return this;
        }

        public WebhookRegisterRequestBuilder withServerId(String serverId) {
            this.serverId = serverId;
            return this;
        }
    }
}
