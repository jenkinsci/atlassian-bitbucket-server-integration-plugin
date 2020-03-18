package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class BitbucketRefChange {

    private final String fromHash;
    private final BitbucketRef ref;
    private final String refId;
    private final String toHash;
    private final BitbucketRefChangeType type;

    @JsonCreator
    public BitbucketRefChange(
            @JsonProperty("ref") BitbucketRef ref,
            @JsonProperty("refId") String refId,
            @JsonProperty("fromHash") String fromHash,
            @JsonProperty("toHash") String toHash,
            @JsonProperty("type") BitbucketRefChangeType type) {
        this.ref = requireNonNull(ref, "ref");
        this.refId = requireNonNull(refId, "refId");
        this.fromHash = requireNonNull(fromHash, "fromHash");
        this.toHash = requireNonNull(toHash, "toHash");
        this.type = requireNonNull(type, "type");
    }

    public String getFromHash() {
        return fromHash;
    }

    public BitbucketRef getRef() {
        return ref;
    }

    public String getRefId() {
        return refId;
    }

    public String getToHash() {
        return toHash;
    }

    public BitbucketRefChangeType getType() {
        return type;
    }
}
