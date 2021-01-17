package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequest {

    private final int id;
    private final BitbucketPullState state;
    private final BitbucketPullRequestRef fromRef;
    private final BitbucketPullRequestRef toRef;

    @JsonCreator
    public BitbucketPullRequest(
            @JsonProperty("id") int id,
            @JsonProperty("state") BitbucketPullState state,
            @JsonProperty("fromRef") BitbucketPullRequestRef fromRef,
            @JsonProperty("toRef") BitbucketPullRequestRef toRef) {
        this.id = requireNonNull(id, "id");
        this.state = requireNonNull(state, "state");
        this.fromRef = requireNonNull(fromRef, "fromRef");
        this.toRef = requireNonNull(toRef, "toRef");
    }

    //Requests don't have to have the same state to be equal
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketPullRequest that = (BitbucketPullRequest) o;
        return id == that.id &&
               fromRef.equals(that.fromRef) &&
               toRef.equals(that.toRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fromRef, toRef);
    }

    public int getId() {
        return id;
    }

    public BitbucketPullState getState() {
        return state;
    }

    public BitbucketPullRequestRef getFromRef() {
        return fromRef;
    }

    public BitbucketPullRequestRef getToRef() {
        return toRef;
    }
}