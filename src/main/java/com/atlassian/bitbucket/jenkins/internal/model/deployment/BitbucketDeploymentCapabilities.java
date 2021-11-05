package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDeploymentCapabilities {

    private final boolean isDeploymentsSupported;

    public BitbucketDeploymentCapabilities(boolean isDeploymentsSupported) {
        // The deployments capabilities endpoint responds with an empty {} object, so we are adding this "transient"
        // property to determine if deployments is supported or not.
        this.isDeploymentsSupported = isDeploymentsSupported;
    }

    @JsonCreator
    @SuppressWarnings("unused") // used by jackson
    private BitbucketDeploymentCapabilities() {
        // If this object was successfully deserialized from the capabilities API response, then
        // isDeploymentsSupported should be true.
        this(true);
    }

    public boolean isDeploymentsSupported() {
        return isDeploymentsSupported;
    }
}
