package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

/**
 * The details of an environment that was deployed to.
 *
 * @since deployments
 */
public final class BitbucketDeploymentEnvironment {

    private static final String DISPLAY_NAME = "displayName";
    private static final String KEY = "key";
    private static final String TYPE = "type";
    private static final String URL = "url";

    private final String key;
    private final String name;
    private final String type;
    private final String url;

    private BitbucketDeploymentEnvironment(Builder builder) {
        key = builder.key;
        name = builder.name;
        type = builder.type == null ? null : builder.type.name();
        url = builder.url;
    }

    @JsonCreator
    public BitbucketDeploymentEnvironment(@JsonProperty(KEY) String key,
                                          @JsonProperty(DISPLAY_NAME) String name,
                                          @JsonProperty(TYPE) String type,
                                          @JsonProperty(URL) String url) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketDeploymentEnvironment that = (BitbucketDeploymentEnvironment) o;
        return Objects.equals(key, that.key) && Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) && Objects.equals(url, that.url);
    }

    /**
     * @return a unique identifier for this environment
     */
    @JsonProperty(KEY)
    @Nonnull
    public String getKey() {
        return key;
    }

    /**
     * @return a human-readable name for this environment
     */
    @JsonProperty(DISPLAY_NAME)
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * @return the {@link BitbucketDeploymentEnvironmentType} of environment, or {@code null} to indicate no type
     */
    @JsonProperty(TYPE)
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * @return the URL of the environment, or {@code null} to indicate no URL
     */
    @JsonProperty(URL)
    @Nullable
    public String getUrl() {
        return url;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, name, type, url);
    }

    @Override
    public String toString() {
        return "BitbucketDeploymentEnvironment{" +
               "key='" + key + '\'' +
               ", name='" + name + '\'' +
               ", type='" + type + '\'' +
               ", url='" + url + '\'' +
               '}';
    }

    public static class Builder {

        private final String key;
        private final String name;

        private BitbucketDeploymentEnvironmentType type;
        private String url;

        public Builder(@Nonnull String key, @Nonnull String name) {
            this.key = requireNonNull(stripToNull(key), "key");
            this.name = requireNonNull(stripToNull(name), "name");
        }

        @Nonnull
        public BitbucketDeploymentEnvironment build() {
            return new BitbucketDeploymentEnvironment(this);
        }

        @Nonnull
        public Builder type(@Nullable BitbucketDeploymentEnvironmentType value) {
            type = value;

            return this;
        }

        @Nonnull
        public Builder url(@Nullable String value) {
            url = stripToNull(value);

            return this;
        }
    }
}
