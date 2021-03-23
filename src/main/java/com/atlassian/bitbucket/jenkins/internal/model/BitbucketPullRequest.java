package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import static java.util.Objects.requireNonNull;

/**
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequest {

    private final long id;
    private final BitbucketPullRequestState state;
    private final BitbucketPullRequestRef fromRef;
    private final BitbucketPullRequestRef toRef;
    private final long updatedDate;

    @JsonCreator
    public BitbucketPullRequest(
            @JsonProperty("id") long id,
            @JsonProperty("state") BitbucketPullRequestState state,
            @JsonProperty("fromRef") BitbucketPullRequestRef fromRef,
            @JsonProperty("toRef") BitbucketPullRequestRef toRef,
            @JsonProperty("updatedDate") long updatedDate) {
        this.id = id;
        this.state = requireNonNull(state, "state");
        this.fromRef = requireNonNull(fromRef, "fromRef");
        this.toRef = requireNonNull(toRef, "toRef");
        this.updatedDate = updatedDate;
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

    public long getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fromRef, toRef);
    }

    public long getId() {
        return id;
    }

    public BitbucketPullRequestState getState() {
        return state;
    }

    public BitbucketPullRequestRef getFromRef() {
        return fromRef;
    }

    public BitbucketPullRequestRef getToRef() {
        return toRef;
    }
}