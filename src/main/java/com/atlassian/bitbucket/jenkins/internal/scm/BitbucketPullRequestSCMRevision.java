package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

public class BitbucketPullRequestSCMRevision extends ChangeRequestSCMRevision<BitbucketPullRequestSCMHead> {

    private static final long serialVersionUID = 1L;

    private final String change;

    public BitbucketPullRequestSCMRevision(@NonNull BitbucketPullRequestSCMHead head,
                                           BitbucketPullRequest pullRequest) {
        super(head, new BitbucketSCMRevision(head.getTarget(), pullRequest.getToRef().getLatestCommit()));
        this.change = pullRequest.getFromRef().getLatestCommit();
    }

    /**
     * The commit hash of the pull request source branch (source ref)
     */
    public String getChange() {
            return change;
        }

    @Override
    public boolean equivalent(ChangeRequestSCMRevision<?> o) {

        if (!(o instanceof BitbucketPullRequestSCMRevision)) {
            return false;
        }

        BitbucketPullRequestSCMRevision other = (BitbucketPullRequestSCMRevision) o;
        return getHead().equals(other.getHead()) && getChange().equals(other.getChange());
    }

    @Override
    protected int _hashCode() {
        return change.hashCode();
    }

    @Override
    public String toString() {
        return getTarget() + "+" + change;
    }
}
