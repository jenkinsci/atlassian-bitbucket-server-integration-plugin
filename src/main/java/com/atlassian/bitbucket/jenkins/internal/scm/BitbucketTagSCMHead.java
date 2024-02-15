package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.TagSCMHead;

public class BitbucketTagSCMHead extends BitbucketSCMHead implements TagSCMHead {

    private static final String REFS_HEADS_PREFIX = "refs/heads/";
    private static final long UNKNOWN_TIMESTAMP = -1;

    public BitbucketTagSCMHead(String name) {
        super(name, null, UNKNOWN_TIMESTAMP);
    }

    public BitbucketTagSCMHead(BitbucketTag tag) {
        super(tag.getDisplayId(), tag.getLatestCommit(), UNKNOWN_TIMESTAMP);
    }

    @Override
    public String getFullRef() {
        return REFS_HEADS_PREFIX + getName();
    }

    @Override
    public long getTimestamp() {
        return 0;
    }

    /**
     * Migration from {@link GitBranchSCMHead} to {@link BitbucketBranchSCMHead}.
     */
    @Extension
    public static class SCMHeadMigrationImpl extends SCMHeadMigration<BitbucketSCMSource, GitTagSCMHead,
            GitBranchSCMRevision> {

        public SCMHeadMigrationImpl() {
            super(BitbucketSCMSource.class, GitTagSCMHead.class, GitBranchSCMRevision.class);
        }

        @Override
        public SCMHead migrate(@NonNull BitbucketSCMSource source, @NonNull GitTagSCMHead head) {
            return new BitbucketTagSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@NonNull BitbucketSCMSource source, @NonNull GitBranchSCMRevision revision) {
            return new BitbucketSCMRevision(new BitbucketTagSCMHead(revision.getHead().getName()),
                    revision.getHash());
        }
    }
}
