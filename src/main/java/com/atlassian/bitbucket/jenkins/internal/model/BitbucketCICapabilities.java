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

    private final Set<String> ciCapabilities;

    @JsonCreator
    public BitbucketCICapabilities(@JsonProperty(value = "application-buildStatus") Set<String> ciCapabilities) {
        this.ciCapabilities = unmodifiableSet(requireNonNull(ciCapabilities, "Application hooks events unavailable"));
    }

    public Set<String> getCiCapabilities() {
        return ciCapabilities;
    }

    public boolean supportsRichBuildStatus() {
        return ciCapabilities.contains(RICH_BUILD_STATUS_CAPABILITY);
    }
}
