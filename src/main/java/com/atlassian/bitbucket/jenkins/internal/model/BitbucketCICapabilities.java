package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCICapabilities {

    private static final String RICH_BUILD_STATUS_CAPABILITY = "richBuildStatus";
    private static final String CANCELLED_BUILD_STATE_CAPABILITIES = "cancelledStatus";
    private final Set<String> ciCapabilities;

    @JsonCreator
    public BitbucketCICapabilities(@JsonProperty(value = "buildStatus") Set<String> ciCapabilities) {
        this.ciCapabilities = unmodifiableSet(requireNonNull(ciCapabilities, "Build status capability missing."));
    }

    public Set<String> getCiCapabilities() {
        return ciCapabilities;
    }

    public boolean supportsCancelledBuildStates() {
        return ciCapabilities.contains(CANCELLED_BUILD_STATE_CAPABILITIES);
    }

    public boolean supportsRichBuildStatus() {
        return ciCapabilities.contains(RICH_BUILD_STATUS_CAPABILITY);
    }
}
