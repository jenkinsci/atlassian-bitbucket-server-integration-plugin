package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCICapabilities {

    private static final String RICH_BUILD_STATUS_CAPABILITY = "richBuildStatus";
    private static final List<String> BUILD_STATE_CAPABILITIES = Arrays.asList("unknownStatus", "cancelledStatus");
    private final Set<String> ciCapabilities;

    @JsonCreator
    public BitbucketCICapabilities(@JsonProperty(value = "buildStatus") Set<String> ciCapabilities) {
        this.ciCapabilities = unmodifiableSet(requireNonNull(ciCapabilities, "Build status capability missing."));
    }

    public Set<String> getCiCapabilities() {
        return ciCapabilities;
    }

    public boolean supportsCancelledAndUnknownBuildStates() {
        return ciCapabilities.containsAll(BUILD_STATE_CAPABILITIES);
    }

    public boolean supportsRichBuildStatus() {
        return ciCapabilities.contains(RICH_BUILD_STATUS_CAPABILITY);
    }
}
