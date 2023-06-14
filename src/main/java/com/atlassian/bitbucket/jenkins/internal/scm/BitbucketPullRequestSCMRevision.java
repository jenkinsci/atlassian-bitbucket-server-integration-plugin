package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

import java.util.Objects;

public class BitbucketPullRequestSCMRevision extends ChangeRequestSCMRevision<BitbucketPullRequestSCMHead> {

    private static final long serialVersionUID = 1L;

    private final String commitHash;

    public BitbucketPullRequestSCMRevision(BitbucketPullRequestSCMHead head) {
        super(head, new BitbucketSCMRevision(head.getTarget(), head.getPullRequest().getToLatestCommit()));
        this.commitHash = head.getPullRequest().getFromLatestCommit();
    }

    /**
     * The commit hash of the pull request source branch (source ref)
     */
    public String getCommitHash() {
        return commitHash;
    }

    @Override
    public boolean equivalent(ChangeRequestSCMRevision<?> o) {

        if (!(o instanceof BitbucketPullRequestSCMRevision)) {
            return false;
        }

        BitbucketPullRequestSCMRevision other = (BitbucketPullRequestSCMRevision) o;
        return getHead().equals(other.getHead()) && commitHash.equals(other.commitHash);
    }

    @Override
    protected int _hashCode() {
        return Objects.hash(getHead(), commitHash);
    }

    @Override
    public String toString() {
        return getTarget() + "+" + commitHash;
    }
}
