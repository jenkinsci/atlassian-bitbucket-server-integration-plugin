package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketNamedLink that = (BitbucketNamedLink) o;
        return href.equals(that.href) &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(href, name);
    }

    public String getHref() {
        return href;
    }

    @Nullable
    public String getName() {
        return name;
    }
}
