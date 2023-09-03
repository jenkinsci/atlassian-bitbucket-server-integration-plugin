package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMRevision;

import java.util.Objects;

/**
 * @since 4.0.0
 */
public class BitbucketSCMRevision extends SCMRevision {

    private static final long serialVersionUID = 1L;
    private final String commitHash;

    public BitbucketSCMRevision(@NonNull BitbucketSCMHead head, @CheckForNull String commitHash) {
        super(head);
        this.commitHash = commitHash;
    }

    @CheckForNull
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
        if (commitHash == null) {
            return Objects.equals(getHead(), that.getHead());
        }

        return commitHash.equals(that.commitHash);
    }

    @Override
    public int hashCode() {
        if (commitHash == null) {
            return getHead().hashCode();
        }

        return commitHash.hashCode();
    }

    @Override
    public String toString() {
        if (commitHash == null) {
            return getHead().toString();
        }

        return commitHash;
    }
}
