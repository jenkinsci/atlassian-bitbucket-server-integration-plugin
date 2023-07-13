package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;

import static java.util.Objects.requireNonNull;

/**
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequest {

    private final String description;
    private final long id;
    private final BitbucketPullRequestRef fromRef;
    private final BitbucketPullRequestState state;
    private final String title;
    private final BitbucketPullRequestRef toRef;
    private final long updatedDate;

    @JsonCreator
    public BitbucketPullRequest(
            @JsonProperty("id") long id,
            @JsonProperty("state") BitbucketPullRequestState state,
            @JsonProperty("fromRef") BitbucketPullRequestRef fromRef,
            @JsonProperty("toRef") BitbucketPullRequestRef toRef,
            @JsonProperty("updatedDate") long updatedDate,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description) {
        this.id = id;
        this.state = requireNonNull(state, "state");
        this.fromRef = requireNonNull(fromRef, "fromRef");
        this.toRef = requireNonNull(toRef, "toRef");
        this.updatedDate = updatedDate;
        this.title = requireNonNull(title, "title");
        this.description = description;
    }

    /**
     * @since 4.0.0
     */
    @CheckForNull
    public String getDescription() {
        return description;
    }

    public long getId() {
        return id;
    }

    public BitbucketPullRequestRef getFromRef() {
        return fromRef;
    }

    public BitbucketPullRequestState getState() {
        return state;
    }

    /**
     * @since 4.0.0
     */
    public String getTitle() {
        return title;
    }

    public BitbucketPullRequestRef getToRef() {
        return toRef;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }
}
