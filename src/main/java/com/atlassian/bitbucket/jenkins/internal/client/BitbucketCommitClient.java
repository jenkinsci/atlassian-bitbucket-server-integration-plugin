package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCommit;

/**
 * @since JENKINS-73267
 */
public interface BitbucketCommitClient {

    /**
     * Gets the commit details for the given identifier
     *
     * @param identifier the commit hash or the name of the branch to get the commit from
     *
     * @return commit details corresponding to the given identifier
     */
    BitbucketCommit getCommit(String identifier);
}
