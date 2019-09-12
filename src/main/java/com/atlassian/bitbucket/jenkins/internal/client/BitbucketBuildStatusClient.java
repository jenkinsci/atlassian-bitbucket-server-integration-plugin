package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;

/**
 * Client to post build status to remote server
 */
public interface BitbucketBuildStatusClient {

    void post(BitbucketBuildStatus buildStatus);
}
