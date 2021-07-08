package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class WebhookRegisterRequest {

    private static final int MAX_WEBHOOK_NAME_LENGTH = 255;

    private final String jenkinsUrl;
    private final boolean isMirror;
    private final String name;
    private final String projectKey;
    private final String repoSlug;
    private final boolean triggerOnPullRequest;
    private final boolean triggerOnRefChange;

    public WebhookRegisterRequest(Builder builder) {
        this.projectKey = requireNonNull(builder.projectKey);
        this.repoSlug = requireNonNull(builder.repoSlug);
        this.name = requireNonNull(builder.serverId);
        this.jenkinsUrl = requireNonNull(builder.jenkinsUrl);
        this.isMirror = builder.isMirror;
        this.triggerOnRefChange = builder.triggerOnRefChange;
        this.triggerOnPullRequest = builder.triggerOnPullRequest;
    }

    public String getName() {
        return name;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getRepoSlug() {
        return repoSlug;
    }

    public boolean isMirror() {
        return isMirror;
    }

    public boolean isTriggerOnPullRequest() {
        return triggerOnPullRequest;
    }

    public boolean isTriggerOnRefChange() {
        return triggerOnRefChange;
    }

    public static class Builder {

        private final String projectKey;
        private final String repoSlug;
        private boolean isMirror;
        private String jenkinsUrl;
        private String serverId;
        private boolean triggerOnPullRequest;
        private boolean triggerOnRefChange;

        private Builder(String projectKey, String repoSlug) {
            this.projectKey = projectKey;
            this.repoSlug = repoSlug;
        }

        public static Builder aRequest(String project, String repoSlug) {
            return new Builder(project, repoSlug);
        }

        public WebhookRegisterRequest build() {
            return new WebhookRegisterRequest(this);
        }

        public Builder isMirror(boolean isMirror) {
            this.isMirror = isMirror;
            return this;
        }

        public Builder shouldTriggerOnPullRequest(boolean triggerOnPullRequest) {
            this.triggerOnPullRequest = triggerOnPullRequest;
            return this;
        }

        public Builder shouldTriggerOnRefChange(boolean triggerOnRefChange) {
            this.triggerOnRefChange = triggerOnRefChange;
            return this;
        }

        public Builder withJenkinsBaseUrl(String jenkinsUrl) {
            this.jenkinsUrl = jenkinsUrl;
            return this;
        }

        public Builder withName(String name) {
            if (name.length() > MAX_WEBHOOK_NAME_LENGTH) {
                throw new IllegalArgumentException(format("Webhook name should be less than %d characters", MAX_WEBHOOK_NAME_LENGTH));
            }
            this.serverId = name;
            return this;
        }
    }
}
