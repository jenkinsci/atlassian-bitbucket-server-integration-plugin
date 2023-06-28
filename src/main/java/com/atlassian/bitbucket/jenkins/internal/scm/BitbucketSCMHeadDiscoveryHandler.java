package com.atlassian.bitbucket.jenkins.internal.scm;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;

import java.util.stream.Stream;

/**
 * TODO: Update doc with relevant methods when BitbucketSCMSource is implemented.
 * Handles the discovery of different head types to be used by the BitbucketSCMSource
 * or processing a {@link BitbucketSCMSourceRequest request}.
 */
public interface BitbucketSCMHeadDiscoveryHandler {

    /**
     * @return as stream of {@link SCMHead heads} to be used for processing the source request
     */
    Stream<? extends SCMHead> discoverHeads();

    /**
     * Creates a {@link SCMRevision revision} for the specified head.
     *
     * @param head the head to create a revision for
     * @return the revision for the specified head
     */
    SCMRevision toRevision(SCMHead head);
}
