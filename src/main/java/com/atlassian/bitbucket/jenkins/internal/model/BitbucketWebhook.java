package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Objects;
import java.util.Set;

import static org.apache.commons.lang3.builder.ToStringStyle.JSON_STYLE;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebhook extends BitbucketWebhookRequest {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String EVENTS = "events";
    private static final String URL = "url";
    private static final String ACTIVE = "active";

    private final int id;

    @JsonCreator
    public BitbucketWebhook(@JsonProperty(value = ID) int id,
                            @JsonProperty(value = NAME) String name,
                            @JsonProperty(value = EVENTS) Set<String> events,
                            @JsonProperty(value = URL) String url,
                            @JsonProperty(value = ACTIVE) boolean isActive) {
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

    @Override
    public String toString() {
        return new ToStringBuilder(this, JSON_STYLE)
                .append(ID, id)
                .append(NAME, getName())
                .append(URL, getUrl())
                .append(EVENTS, getEvents())
                .append(ACTIVE, isActive())
                .toString();
    }
}
