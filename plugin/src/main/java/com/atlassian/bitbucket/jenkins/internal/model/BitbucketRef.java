package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class BitbucketRef {

    private final String displayId;
    private final String id;
    private final BitbucketRefType type;

    @JsonCreator
    public BitbucketRef(
            @JsonProperty("id") String id,
            @JsonProperty("displayId") String displayId,
            @JsonProperty("type") BitbucketRefType type) {
        this.id = requireNonNull(id, "id");
        this.displayId = requireNonNull(displayId, "displayId");
        this.type = requireNonNull(type, "type");
    }

    public String getDisplayId() {
        return displayId;
    }

    public String getId() {
        return id;
    }

    public BitbucketRefType getType() {
        return type;
    }
}
