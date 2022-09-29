package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;

import java.util.function.Consumer;

/**
 * Client to post build status to remote server
 */
public interface BitbucketBuildStatusClient {

    void post(BitbucketBuildStatus.Builder buildStatusBuilder, Consumer<BitbucketBuildStatus> beforePost);
}
