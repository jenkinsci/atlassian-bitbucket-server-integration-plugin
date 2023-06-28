package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceTrait;

/**
 * Marker class for all {@link SCMSourceTrait traits} that are used directly in
 * {@link BitbucketSCMSource#retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)}.
 *
 * @since 4.0.0
 */
public abstract class BitbucketSCMSourceTrait extends SCMSourceTrait {
}
