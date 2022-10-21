package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.http.RetryOnRateLimitConfig;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import okhttp3.HttpUrl;

import java.util.function.Consumer;

import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.REQUEST_RETRY_MAX_ATTEMPTS;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BUILD_STATUS_VERSION = "1.0";
    private static final int maxAttempts = Integer.parseInt(System.getProperty(REQUEST_RETRY_MAX_ATTEMPTS, "3"));
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String revisionSha;

    BitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String revisionSha) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.revisionSha = requireNonNull(stripToNull(revisionSha), "revisionSha");
    }

    @Override
    public void post(BitbucketBuildStatus.Builder buildStatusBuilder, Consumer<BitbucketBuildStatus> beforePost) {
        BitbucketBuildStatus buildStatus = buildStatusBuilder.legacy().build();
        beforePost.accept(buildStatus);
        
        HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                .addPathSegment("rest")
                .addPathSegment("build-status")
                .addPathSegment(BUILD_STATUS_VERSION)
                .addPathSegment("commits")
                .addPathSegment(revisionSha)
                .build();
        bitbucketRequestExecutor.makePostRequest(url, buildStatus, new RetryOnRateLimitConfig(maxAttempts));
    }
}
