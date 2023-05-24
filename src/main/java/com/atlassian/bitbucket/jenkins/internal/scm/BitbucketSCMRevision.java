package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMRevision;

public class BitbucketSCMRevision extends SCMRevision {

    private static final long serialVersionUID = 1L;
    private final String commitHash;

    public BitbucketSCMRevision(@NonNull BitbucketSCMHead head, String commitHash) {
        super(head);
        this.commitHash = commitHash;
    }

    public String getCommitHash() {
        return commitHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BitbucketSCMRevision that = (BitbucketSCMRevision) o;

        return commitHash.equals(that.commitHash);
    }

    @Override
    public int hashCode() {
        return commitHash.hashCode();
    }

    @Override
    public String toString() {
        return commitHash;
    }

}
