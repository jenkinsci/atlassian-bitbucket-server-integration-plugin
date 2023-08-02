package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefChange;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;

public class BitbucketBranchSCMHead extends BitbucketSCMHead {

    public BitbucketBranchSCMHead(String name) {
        super(name, null, -1);
    }

    public BitbucketBranchSCMHead(BitbucketDefaultBranch branch) {
        super(branch.getDisplayId(), branch.getLatestCommit(), -1);
    }

    public BitbucketBranchSCMHead(BitbucketRefChange refChange) {
        super(refChange.getRef().getDisplayId(), refChange.getToHash(), -1);
    }

    /**
     * Migration from {@link GitBranchSCMHead} to {@link BitbucketBranchSCMHead}.
     */
    @Extension
    public static class SCMHeadMigrationImpl extends SCMHeadMigration<BitbucketSCMSource, GitBranchSCMHead,
            GitBranchSCMRevision> {

        public SCMHeadMigrationImpl() {
            super(BitbucketSCMSource.class, GitBranchSCMHead.class, GitBranchSCMRevision.class);
        }

        @Override
        public SCMHead migrate(@NonNull BitbucketSCMSource source, @NonNull GitBranchSCMHead head) {
            return new BitbucketBranchSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@NonNull BitbucketSCMSource source, @NonNull GitBranchSCMRevision revision) {
            return new BitbucketSCMRevision(new BitbucketBranchSCMHead(revision.getHead().getName()),
                    revision.getHash());
        }
    }
}
