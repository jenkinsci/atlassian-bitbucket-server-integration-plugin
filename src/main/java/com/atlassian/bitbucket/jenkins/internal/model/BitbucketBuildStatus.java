package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitbucketBuildStatus {

    private final String buildNumber;
    private final String description;
    private final Long duration;
    private final String key;
    private final String name;
    private final String parent;
    private final String ref;
    private final BuildState state;
    private final TestResults testResults;
    private final String url;

    @JsonCreator
    public BitbucketBuildStatus(@JsonProperty("buildNumber") String buildNumber,
                                @JsonProperty("description") String description,
                                @JsonProperty("duration") Long duration,
                                @JsonProperty("key") String key,
                                @JsonProperty("name") String name,
                                @JsonProperty("parent") String parent,
                                @JsonProperty("ref") String ref,
                                @JsonProperty("state") BuildState state,
                                @JsonProperty("testResults") TestResults testResults,
                                @JsonProperty("url") String url) {
        requireNonNull(key, "key");
        requireNonNull(state, "state");
        requireNonNull(url, "url");
        this.buildNumber = buildNumber;
        this.description = description;
        this.duration = duration;
        this.key = key;
        this.name = name;
        this.parent = parent;
        this.ref = ref;
        this.state = state;
        this.testResults = testResults;
        this.url = url;
    }

    @JsonProperty(value = "buildNumber")
    public String getBuildNumber() {
        return buildNumber;
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

    @JsonProperty(value = "parent")
    @Nullable
    public String getParent() {
        return parent;
    }

    @JsonProperty(value = "ref")
    @Nullable
    public String getRef() {
        return ref;
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

        private String buildNumber;
        private String description;
        private Long duration;
        private String key;
        private String name;
        private String parent;
        private String ref;
        private BuildState state;
        private TestResults testResults;
        private String url;

        public Builder(String key, BuildState state, String url) {
            this.key = key;
            this.state = state;
            this.url = url;
        }

        public BitbucketBuildStatus build() {
            return new BitbucketBuildStatus(buildNumber, description, duration, key, name, parent, ref, state, testResults, url);
        }

        public Builder setBuildNumber(String buildNumber) {
            this.buildNumber = buildNumber;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setDuration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setParent(String parent) {
            this.parent = parent;
            return this;
        }

        public Builder setRef(@Nullable String ref) {
            if (ref != null && !ref.startsWith("refs/")) {
                Logger.getLogger(BitbucketBuildStatus.class.getName()).warning(
                        format("Supplied ref '%s' does not start with 'refs/', ignoring", ref));
                return this;
            }
            this.ref = ref;
            return this;
        }

        public Builder setTestResults(@Nullable TestResults testResults) {
            this.testResults = testResults;
            return this;
        }
    }
}
