package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

/**
 * @since JENKINS-73267
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCommit {

    private final long committerTimestamp;
    private final String displayId;
    private final String id;
    private final String message;

    @JsonCreator
    public BitbucketCommit(
            @JsonProperty("id") String id,
            @JsonProperty("displayId") String displayId,
            @JsonProperty("committerTimestamp") long committerTimestamp,
            @JsonProperty("message") String message) {
        this.id = requireNonNull(id, "id");
        this.displayId = requireNonNull(displayId, "displayId");
        this.message = message;
        this.committerTimestamp = committerTimestamp;
    }

    public long getCommitterTimestamp() {
        return committerTimestamp;
    }

    public String getDisplayId() {
        return displayId;
    }

    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }
}
