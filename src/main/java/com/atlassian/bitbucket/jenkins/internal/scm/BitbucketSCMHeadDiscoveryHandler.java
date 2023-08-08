package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.TaskListener;
import jenkins.scm.api.*;

import java.util.stream.Stream;

/**
 * Handles the discovery of different head types to be used by the
 * {@link BitbucketSCMSource#retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)} method as part
 * of processing a {@link BitbucketSCMSourceRequest request}.
 *
 * @since 4.0.0
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
