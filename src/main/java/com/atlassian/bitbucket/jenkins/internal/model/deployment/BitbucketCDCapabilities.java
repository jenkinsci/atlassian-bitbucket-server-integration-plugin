package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCDCapabilities {

    public static final String DEPLOYMENTS_CAPABILITY_VALUE = "deployments";

    private final Set<String> cdCapabilities;

    @JsonCreator
    public BitbucketCDCapabilities(Set<String> cdCapabilities) {
        this.cdCapabilities = unmodifiableSet(requireNonNull(cdCapabilities, "cdCapabilities"));
    }

    public Set<String> getCdCapabilities() {
        return cdCapabilities;
    }

    public boolean supportsDeployments() {
        return cdCapabilities.contains(DEPLOYMENTS_CAPABILITY_VALUE);
    }
}
