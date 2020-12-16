package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequest {

    private final int id;
    private final BitbucketPullState state;
    private final BitbucketPullRef fromRef;
    private final BitbucketPullRef toRef;

    @JsonCreator
    public BitbucketPullRequest(
            @JsonProperty("id") int id,
            @JsonProperty("state") BitbucketPullState state,
            @JsonProperty("fromRef") BitbucketPullRef fromRef,
            @JsonProperty("toRef") BitbucketPullRef toRef) {
        this.id = requireNonNull(id, "id");
        this.state = requireNonNull(state, "state");
        this.fromRef = requireNonNull(fromRef, "fromRef");
        this.toRef = requireNonNull(toRef, "toRef");
    }

    public int getId() {
        return id;
    }

    public BitbucketPullState getState() {
        return state;
    }

    public BitbucketPullRef getFromRef() {
        return fromRef;
    }

    public BitbucketPullRef getToRef() {
        return toRef;
    }
}