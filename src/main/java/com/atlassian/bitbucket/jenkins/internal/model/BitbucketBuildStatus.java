package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitbucketBuildStatus {

    private final String description;
    private final Long duration;
    private final String key;
    private final String name;
    private final String resultKey;
    private final String server;
    private final BuildState state;
    private final TestResults testResults;
    private final String url;

    @JsonCreator
    public BitbucketBuildStatus(@JsonProperty("description") String description,
                                @JsonProperty("duration") Long duration,
                                @JsonProperty("key") String key,
                                @JsonProperty("name") String name,
                                @JsonProperty("resultKey") String resultKey,
                                @JsonProperty("server") String server,
                                @JsonProperty("state") BuildState state,
                                @JsonProperty("testResults") TestResults testResults,
                                @JsonProperty("url") String url) {
        requireNonNull(key, "key");
        requireNonNull(state, "state");
        requireNonNull(url, "url");
        this.description = description;
        this.duration = duration;
        this.key = key;
        this.name = name;
        this.resultKey = resultKey;
        this.server = server;
        this.state = state;
        this.testResults = testResults;
        this.url = url;
    }

    @JsonProperty(value = "description")
    public String getDescription() {
        return description;
    }

    @JsonProperty(value = "duration")
    @Nullable
    public Long getDuration() {
        return duration;
    }

    @JsonProperty(value = "key")
    public String getKey() {
        return key;
    }

    @JsonProperty(value = "name")
    public String getName() {
        return name;
    }

    @JsonProperty(value = "resultKey")
    @Nullable
    public String getResultKey() {
        return resultKey;
    }

    @JsonProperty(value = "server")
    @Nullable
    public String getServer() {
        return server;
    }

    @JsonProperty(value = "state")
    public String getState() {
        return state.toString();
    }

    @JsonProperty(value = "testResults")
    @Nullable
    public TestResults getTestResults() {
        return testResults;
    }

    @JsonProperty(value = "url")
    public String getUrl() {
        return url;
    }

    public static class Builder {

        private String description;
        private Long duration;
        private String key;
        private String name;
        private String resultKey;
        private String server;
        private BuildState state;
        private TestResults testResults;
        private String url;

        public Builder(String key, BuildState state, String url) {
            this.key = key;
            this.state = state;
            this.url = url;
        }

        public BitbucketBuildStatus build() {
            return new BitbucketBuildStatus(description, duration, key, name, resultKey, server, state, testResults, url);
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setResultKey(String resultKey) {
            this.resultKey = resultKey;
            return this;
        }

        public Builder setServer(String server) {
            this.server = server;
            return this;
        }

        public Builder setTestResults(@Nullable TestResults testResults) {
            this.testResults = testResults;
            return this;
        }
    }
}
