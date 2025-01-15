package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefChange;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.TagSCMHead;

public class BitbucketTagSCMHead extends BitbucketSCMHead implements TagSCMHead {

    private static final String REFS_TAGS_PREFIX = "refs/tags/";
    private static final long UNKNOWN_TIMESTAMP = -1;

    public BitbucketTagSCMHead(String name) {
        super(name, null, UNKNOWN_TIMESTAMP);
    }

    public BitbucketTagSCMHead(BitbucketTag tag) {
        super(tag.getDisplayId(), tag.getLatestCommit(), UNKNOWN_TIMESTAMP);
    }

    public BitbucketTagSCMHead(BitbucketRefChange refChange) {
        super(refChange.getRef().getDisplayId(), refChange.getToHash(), UNKNOWN_TIMESTAMP);
    }

    @Override
    public String getFullRef() {
        return REFS_TAGS_PREFIX + getName();
    }

    @Override
    public long getTimestamp() {
        return UNKNOWN_TIMESTAMP;
    }

    /**
     * Migration from {@link GitTagSCMHead} to {@link BitbucketTagSCMHead}.
     */
    @Extension
    public static class SCMHeadMigrationImpl extends SCMHeadMigration<BitbucketSCMSource, GitTagSCMHead,
            GitTagSCMRevision> {

        public SCMHeadMigrationImpl() {
            super(BitbucketSCMSource.class, GitTagSCMHead.class, GitTagSCMRevision.class);
        }

        @Override
        public SCMHead migrate(@NonNull BitbucketSCMSource source, @NonNull GitTagSCMHead head) {
            return new BitbucketTagSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@NonNull BitbucketSCMSource source, @NonNull GitTagSCMRevision revision) {
            return new BitbucketSCMRevision(new BitbucketTagSCMHead(revision.getHead().getName()),
                    revision.getHash());
        }
    }
}
