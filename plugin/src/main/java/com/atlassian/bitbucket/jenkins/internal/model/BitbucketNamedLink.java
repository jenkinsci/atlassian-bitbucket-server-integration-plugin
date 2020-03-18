package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketNamedLink {

    private final String href;
    private final String name;

    @JsonCreator
    public BitbucketNamedLink(
            @JsonProperty(value = "name") String name,
            @JsonProperty(value = "href", required = true) String href) {
        this.name = name;
        this.href = requireNonNull(href, "href");
    }

    public String getHref() {
        return href;
    }

    @Nullable
    public String getName() {
        return name;
    }
}
