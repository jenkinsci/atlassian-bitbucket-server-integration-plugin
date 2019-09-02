package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketBuildStatus {

    private final String description;
    private final String key;
    private final String name;
    private final BuildState state;
    private final String url;

    public BitbucketBuildStatus(String description, String key, String name, BuildState state, String url) {
        this.description = description;
        this.key = key;
        this.name = name;
        this.state = state;
        this.url = url;
    }

    @JsonProperty(value = "description")
    public String getDescription() {
        return description;
    }

    @JsonProperty(value = "key", required = true)
    public String getKey() {
        return key;
    }

    @JsonProperty(value = "name")
    public String getName() {
        return name;
    }

    @JsonProperty(value = "state", required = true)
    public String getState() {
        return state.toString();
    }

    @JsonProperty(value = "url", required = true)
    public String getUrl() {
        return url;
    }

    public static class Builder {

        private String description;
        private String key;
        private String name;
        private BuildState state;
        private String url;

        public Builder(String key, BuildState state, String url) {
            this.key = key;
            this.state = state;
            this.url = url;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public BitbucketBuildStatus build() {
            return new BitbucketBuildStatus(description, key, name, state, url);
        }
    }
}
