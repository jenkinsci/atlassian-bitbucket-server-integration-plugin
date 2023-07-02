package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.Action;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.Objects;

public class BitbucketRevisionAction implements Action {

    private final BitbucketSCMRepository bitbucketSCMRepository;
    private final String refName;
    private final String revisionSha1;

    public BitbucketRevisionAction(BitbucketSCMRepository bitbucketSCMRepository, @Nullable String refName,
                                   String revisionSha1) {
        this.bitbucketSCMRepository = bitbucketSCMRepository;
        this.refName = refName;
        this.revisionSha1 = revisionSha1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketRevisionAction that = (BitbucketRevisionAction) o;
        return Objects.equals(bitbucketSCMRepository, that.bitbucketSCMRepository) &&
               Objects.equals(refName, that.refName) &&
               Objects.equals(revisionSha1, that.revisionSha1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bitbucketSCMRepository, refName, revisionSha1);
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    public BitbucketSCMRepository getBitbucketSCMRepo() {
        return bitbucketSCMRepository;
    }

    @CheckForNull
    public String getRefName() {
        return refName;
    }

    public String getRevisionSha1() {
        return revisionSha1;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
