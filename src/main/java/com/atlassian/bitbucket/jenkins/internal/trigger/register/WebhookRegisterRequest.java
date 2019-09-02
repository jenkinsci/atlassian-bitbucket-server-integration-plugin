package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import static java.util.Objects.requireNonNull;

public class WebhookRegisterRequest {

    private final String projectKey;
    private final String repoSlug;
    private final String name;
    private final String jenkinsUrl;
    private final boolean isMirror;

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
        private String jenkinsUrl;
        private boolean isMirror;
        private String serverId;

        private Builder(String projectKey, String repoSlug) {
            this.projectKey = projectKey;
            this.repoSlug = repoSlug;
        }

        public static Builder aRequest(String project, String repoSlug) {
            return new Builder(project, repoSlug);
        }

        public Builder withJenkinsBaseUrl(String jenkinsUrl) {
            this.jenkinsUrl = jenkinsUrl;
            return this;
        }

        public WebhookRegisterRequest build() {
            return new WebhookRegisterRequest(projectKey, repoSlug, serverId, jenkinsUrl, isMirror);
        }

        public Builder isMirror(boolean isMirror) {
            this.isMirror = isMirror;
            return this;
        }

        public Builder withName(String serverId) {
            this.serverId = serverId;
            return this;
        }
    }
}
