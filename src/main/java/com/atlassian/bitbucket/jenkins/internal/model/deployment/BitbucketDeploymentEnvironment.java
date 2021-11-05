package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * The details of an environment that was deployed to.
 *
 * @since 3.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitbucketDeploymentEnvironment implements Serializable {

    private static final String DISPLAY_NAME = "displayName";
    private static final String KEY = "key";
    private static final long serialVersionUID = 1L;
    private static final String TYPE = "type";
    private static final String URL = "url";

    private final String key;
    private final String name;
    private final BitbucketDeploymentEnvironmentType type;
    private final URI url;

    @JsonCreator
    public BitbucketDeploymentEnvironment(@JsonProperty(KEY) String key,
                                          @JsonProperty(DISPLAY_NAME) String name,
                                          @CheckForNull @JsonProperty(TYPE) BitbucketDeploymentEnvironmentType type,
                                          @CheckForNull @JsonProperty(URL) URI url) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.url = url;
    }

    public BitbucketDeploymentEnvironment(String key, String name) {
        this(key, name, null, null);
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
    public String getKey() {
        return key;
    }

    /**
     * @return a human-readable name for this environment
     */
    @JsonProperty(DISPLAY_NAME)
    public String getName() {
        return name;
    }

    /**
     * @return the {@link BitbucketDeploymentEnvironmentType} of environment, or {@code null} to indicate no type
     */
    @JsonProperty(TYPE)
    @CheckForNull
    public BitbucketDeploymentEnvironmentType getType() {
        return type;
    }

    /**
     * @return the URL of the environment, or {@code null} to indicate no URL
     */
    @JsonProperty(URL)
    @CheckForNull
    public URI getUrl() {
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
}
