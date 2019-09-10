package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import okhttp3.HttpUrl;

public class BitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BUILD_STATUS_VERSION = "1.0";
    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public void post(String revisionSha, BitbucketBuildStatus buildStatus) {
        HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                .addPathSegment("rest")
                .addPathSegment("build-status")
                .addPathSegment(BUILD_STATUS_VERSION)
                .addPathSegment("commits")
                .addPathSegment(revisionSha)
                .build();
        bitbucketRequestExecutor.makePostRequest(url, buildStatus);
    }
}
