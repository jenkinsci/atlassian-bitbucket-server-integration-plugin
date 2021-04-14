package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketBranch {

    private final String displayId;
    private final String id;
    private final String type;

    @JsonCreator
    public BitbucketBranch(
            @JsonProperty("id") String id,
            @JsonProperty("displayId") String displayId,
            @JsonProperty("type") String type) {
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

    public String getType() {
        return type;
    }
}

