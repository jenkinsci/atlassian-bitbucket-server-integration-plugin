package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketBuildStatus {

    private final String description;
    private final String key;
    private final String name;
    private final BuildState state;
    private final String url;

    @JsonCreator
    public BitbucketBuildStatus(@Nullable @JsonProperty("description") String description,
                                @JsonProperty("key") String key,
                                @Nullable @JsonProperty("name") String name,
                                @JsonProperty("state") BuildState state,
                                @JsonProperty("url") String url) {
        this.description = description;
        this.key = requireNonNull(key, "key");
        this.name = name;
        this.state = requireNonNull(state, "state");
        this.url = requireNonNull(url, "url");
    }

    @JsonProperty(value = "description")
    public String getDescription() {
        return description;
    }

    @JsonProperty(value = "key")
    public String getKey() {
        return key;
    }

    @JsonProperty(value = "name")
    public String getName() {
        return name;
    }

    @JsonProperty(value = "state")
    public String getState() {
        return state.toString();
    }

    @JsonProperty(value = "url")
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

        public BitbucketBuildStatus build() {
            return new BitbucketBuildStatus(description, key, name, state, url);
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }
    }
}
