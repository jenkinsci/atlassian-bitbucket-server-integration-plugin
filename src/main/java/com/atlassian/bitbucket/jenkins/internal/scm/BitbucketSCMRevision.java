package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMRevision;

public class BitbucketSCMRevision extends SCMRevision {

    private static final long serialVersionUID = 1L;
    private final String hash;

    public BitbucketSCMRevision(@NonNull BitbucketSCMHead head, String hash) {
        super(head);
        this.hash = hash;
    }

    public String getHash() {
        return hash;
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

        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return hash;
    }

}
