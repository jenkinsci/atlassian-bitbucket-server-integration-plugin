package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * The details of a deployment.
 *
 * @since 3.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitbucketDeployment {

    private static final String DEPLOYMENT_SEQUENCE_NUMBER = "deploymentSequenceNumber";
    private static final String DESCRIPTION = "description";
    private static final String DISPLAY_NAME = "displayName";
    private static final String ENVIRONMENT = "environment";
    private static final String KEY = "key";
    private static final String STATE = "state";
    private static final String URL = "url";

    private final long deploymentSequenceNumber;
    private final String description;
    private final String displayName;
    private final BitbucketDeploymentEnvironment environment;
    private final String key;
    private final DeploymentState state;
    private final String url;

    @JsonCreator
    public BitbucketDeployment(@JsonProperty(DEPLOYMENT_SEQUENCE_NUMBER) long deploymentSequenceNumber,
                               @JsonProperty(DESCRIPTION) String description,
                               @JsonProperty(DISPLAY_NAME) String displayName,
                               @JsonProperty(ENVIRONMENT) BitbucketDeploymentEnvironment environment,
                               @JsonProperty(KEY) String key,
                               @JsonProperty(STATE) DeploymentState state,
                               @JsonProperty(URL) String url) {
        this.deploymentSequenceNumber = deploymentSequenceNumber;
        this.description = requireNonNull(description, "description");
        this.displayName = requireNonNull(displayName, "displayName");
        this.environment = requireNonNull(environment, "environment");
        this.key = requireNonNull(key, "key");
        this.state = requireNonNull(state, "state");
        this.url = requireNonNull(url, "url");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketDeployment that = (BitbucketDeployment) o;
        return deploymentSequenceNumber == that.deploymentSequenceNumber &&
               Objects.equals(description, that.description) &&
               Objects.equals(displayName, that.displayName) &&
               Objects.equals(environment, that.environment) && Objects.equals(key, that.key) &&
               state == that.state && Objects.equals(url, that.url);
    }

    @JsonProperty(DEPLOYMENT_SEQUENCE_NUMBER)
    public long getDeploymentSequenceNumber() {
        return deploymentSequenceNumber;
    }

    @JsonProperty(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    @JsonProperty(DISPLAY_NAME)
    public String getDisplayName() {
        return displayName;
    }

    @JsonProperty(ENVIRONMENT)
    public BitbucketDeploymentEnvironment getEnvironment() {
        return environment;
    }

    @JsonProperty(KEY)
    public String getKey() {
        return key;
    }

    @JsonProperty(STATE)
    public DeploymentState getState() {
        return state;
    }

    @JsonProperty(URL)
    public String getUrl() {
        return url;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deploymentSequenceNumber, description, displayName, environment, key, state, url);
    }

    @Override
    public String toString() {
        return "BitbucketDeployment{" +
               "deploymentSequenceNumber=" + deploymentSequenceNumber +
               ", description='" + description + '\'' +
               ", displayName='" + displayName + '\'' +
               ", environment=" + environment +
               ", key='" + key + '\'' +
               ", state=" + state +
               ", url='" + url + '\'' +
               '}';
    }
}
