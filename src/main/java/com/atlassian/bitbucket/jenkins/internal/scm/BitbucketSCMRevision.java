package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.AbstractGitSCMSource;

/**
 * @since 4.0.0
 */
public class BitbucketSCMRevision extends AbstractGitSCMSource.SCMRevisionImpl {

    private static final long serialVersionUID = 1L;

    public BitbucketSCMRevision(@NonNull BitbucketSCMHead head, @CheckForNull String commitHash) {
        super(head, commitHash);
    }
}
