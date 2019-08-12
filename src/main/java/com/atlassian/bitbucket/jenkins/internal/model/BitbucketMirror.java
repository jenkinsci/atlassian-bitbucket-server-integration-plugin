package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketMirror {

    private final String baseUrl;
    private final boolean enabled;
    private final String name;

    @JsonCreator
    public BitbucketMirror(
            @JsonProperty(value = "baseUrl", required = true) String baseUrl,
            @JsonProperty(value = "enabled", required = true) boolean enabled,
            @JsonProperty(value = "name", required = true) String name) {
        this.baseUrl = requireNonNull(baseUrl, "baseUrl");
        this.enabled = enabled;
        this.name = requireNonNull(name, "name");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }
}
