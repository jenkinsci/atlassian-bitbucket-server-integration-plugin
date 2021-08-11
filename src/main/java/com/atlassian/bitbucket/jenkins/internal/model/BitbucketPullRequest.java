package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public long getUpdatedDate() {
        return updatedDate;
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
