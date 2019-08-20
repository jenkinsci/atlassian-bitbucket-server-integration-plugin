package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebhook extends BitbucketWebhookRequest {

    private final int id;

    @JsonCreator
    public BitbucketWebhook(@JsonProperty(value = "id") int id,
                            @JsonProperty(value = "name") String name,
                            @JsonProperty(value = "events") Set<String> events,
                            @JsonProperty(value = "url") String url,
                            @JsonProperty(value = "active") boolean isActive) {
        super(name, events, url, isActive);
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketWebhook that = (BitbucketWebhook) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
