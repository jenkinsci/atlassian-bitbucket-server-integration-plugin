package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BUILD_STATUS_VERSION = "1.0";
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String revisionSha;

    BitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String revisionSha) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.revisionSha = requireNonNull(stripToNull(revisionSha), "revisionSha");
    }

    @Override
    public void post(BitbucketBuildStatus buildStatus) {
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
