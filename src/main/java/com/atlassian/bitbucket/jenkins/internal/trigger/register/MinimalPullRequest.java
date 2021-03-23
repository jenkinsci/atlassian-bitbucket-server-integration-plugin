package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;

import java.util.Objects;

/**
 * This class contains the bare minimum of information needed to store. It is deliberately small to reduce the
 * memory footprint of the {@link com.atlassian.bitbucket.jenkins.internal.trigger.register.PullRequestStore}.
 *
 * @since 3.0.0
 */
class MinimalPullRequest {

    private final long id;
    private BitbucketPullRequestState state;
    private final String fromRefDisplayId;
    private final String toRefDisplayId;
    private final long updatedDate;

    public MinimalPullRequest(long id, BitbucketPullRequestState state, String fromRefDisplayId, String toRefDisplayId, long updatedDate) {
        this.id = id;
        this.state = state;
        this.fromRefDisplayId = fromRefDisplayId;
        this.toRefDisplayId = toRefDisplayId;
        this.updatedDate = updatedDate;
    }

    public MinimalPullRequest(BitbucketPullRequest bbsPR) {
        this.id = bbsPR.getId();
        this.state = bbsPR.getState();
        this.fromRefDisplayId = bbsPR.getFromRef().getDisplayId();
        this.toRefDisplayId = bbsPR.getToRef().getDisplayId();
        this.updatedDate = bbsPR.getUpdatedDate();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MinimalPullRequest that = (MinimalPullRequest) o;
        return id == that.id &&
               updatedDate == that.updatedDate &&
               state == that.state &&
               Objects.equals(fromRefDisplayId, that.fromRefDisplayId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, state, fromRefDisplayId, updatedDate);
    }

    public long getId() {
        return id;
    }

    public BitbucketPullRequestState getState() {
        return state;
    }

    public String getFromRefDisplayId() {
        return fromRefDisplayId;
    }

    public String getToRefDisplayId() {
        return toRefDisplayId;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    void setState(BitbucketPullRequestState newState) {
        this.state = newState;
    }
}
