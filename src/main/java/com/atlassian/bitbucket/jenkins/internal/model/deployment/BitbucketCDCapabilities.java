package com.atlassian.bitbucket.jenkins.internal.model.deployment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCDCapabilities {

    private final Map<String, Object> cdCapabilities;

    @JsonCreator
    public BitbucketCDCapabilities(Map<String, Object> cdCapabilities) {
        this.cdCapabilities = unmodifiableMap(requireNonNull(cdCapabilities, "cdCapabilities"));
    }

    public Map<String, Object> getCdCapabilities() {
        return cdCapabilities;
    }
}
