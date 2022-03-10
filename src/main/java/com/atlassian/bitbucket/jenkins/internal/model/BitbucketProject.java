package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketProject {

    private final String key;
    private final String name;
    private final String selfLink;

    @JsonCreator
    public BitbucketProject(
            @CheckForNull @JsonProperty(value = "key", required = true) String key,
            @CheckForNull @JsonProperty("links") Map<String, List<BitbucketNamedLink>> links,
            @CheckForNull @JsonProperty(value = "name", required = true) String name) {
        this.key = Objects.toString(key, "");
        this.name = Objects.toString(name, "");
        if (links != null) {
            List<BitbucketNamedLink> self = links.get("self");
            if (self != null && !self.isEmpty()) { // there should always be exactly one self link.
                selfLink = self.get(0).getHref();
            } else {
                selfLink = null;
            }
        } else {
            selfLink = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketProject that = (BitbucketProject) o;
        return key.equals(that.key) &&
               name.equals(that.name) &&
               Objects.equals(selfLink, that.selfLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, name, selfLink);
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    @CheckForNull
    public String getSelfLink() {
        return selfLink;
    }
}
