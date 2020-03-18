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

    private WebhookRegisterRequest(String projectKey, String repoSlug, String name, String jenkinsUrl,
                                   boolean isMirror) {
        this.projectKey = requireNonNull(projectKey);
        this.repoSlug = requireNonNull(repoSlug);
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

    public String getProjectKey() {
        return projectKey;
    }

    public String getRepoSlug() {
        return repoSlug;
    }

    public boolean isMirror() {
        return isMirror;
    }

    public static class Builder {

        private final String projectKey;
        private final String repoSlug;
        private boolean isMirror;
        private String jenkinsUrl;
        private String serverId;

        private Builder(String projectKey, String repoSlug) {
            this.projectKey = projectKey;
            this.repoSlug = repoSlug;
        }

        public static Builder aRequest(String project, String repoSlug) {
            return new Builder(project, repoSlug);
        }

        public WebhookRegisterRequest build() {
            return new WebhookRegisterRequest(projectKey, repoSlug, serverId, jenkinsUrl, isMirror);
        }

        public Builder isMirror(boolean isMirror) {
            this.isMirror = isMirror;
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
