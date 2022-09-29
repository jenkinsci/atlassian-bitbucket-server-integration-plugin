package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.Objects;
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
    public BitbucketBuildStatus(@JsonProperty("buildNumber") @CheckForNull String buildNumber,
                                @JsonProperty("description") @CheckForNull String description,
                                @JsonProperty("duration") @CheckForNull Long duration,
                                @JsonProperty("key") String key,
                                @JsonProperty("name") @CheckForNull String name,
                                @JsonProperty("parent") @CheckForNull String parent,
                                @JsonProperty("ref") @CheckForNull String ref,
                                @JsonProperty("state") BuildState state,
                                @JsonProperty("testResults") @CheckForNull TestResults testResults,
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
    
    private BitbucketBuildStatus(Builder builder) {
        key = requireNonNull(builder.key, "key");
        url = requireNonNull(builder.url, "url");
        description = builder.description;
        name = builder.name;
        
        if (builder.isLegacy) {
            buildNumber = null;
            duration = null;
            parent = null;
            ref = null;
            testResults = null;
        } else {
            buildNumber = builder.buildNumber;
            duration = builder.duration;
            parent = builder.parent;
            ref = builder.ref;
            testResults = builder.testResults;
        }
        
        if (builder.state == BuildState.CANCELLED && (builder.isLegacy || !builder.isCancelledSupported)) {
            state = BuildState.FAILED;
        } else {
            state = requireNonNull(builder.state, "state");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketBuildStatus that = (BitbucketBuildStatus) o;
        
        return Objects.equals(buildNumber, that.buildNumber) &&
               Objects.equals(description, that.description) &&
               Objects.equals(duration, that.duration) && 
               Objects.equals(key, that.key) &&
               Objects.equals(name, that.name) && 
               Objects.equals(parent, that.parent) &&
               Objects.equals(ref, that.ref) && 
               state == that.state &&
               Objects.equals(testResults, that.testResults) && 
               Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildNumber, description, duration, key, name, parent, ref, state, testResults, url);
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
        private boolean isLegacy = false;
        private boolean isCancelledSupported = true;
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
            return new BitbucketBuildStatus(this);
        }
        
        public Builder legacy() {
            isLegacy = true;
            return this;
        }
        
        public Builder noCancelledState() {
            isCancelledSupported = false;
            return this;
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
