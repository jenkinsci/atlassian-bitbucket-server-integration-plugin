package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class ModernBitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BUILD_STATUS_VERSION = "1.0";
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String revisionSha;
    private final String projectKey;
    private final String repoSlug;

    ModernBitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey, String repoSlug, String revisionSha) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.revisionSha = requireNonNull(stripToNull(revisionSha), "revisionSha");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repoSlug = requireNonNull(stripToNull(repoSlug), "repoSlug");
    }

    @Override
    public void post(BitbucketBuildStatus buildStatus) {
        //builds/projects/PROJECT_1/repos/rep_1/commits/abc1234avcacac
        HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment(BUILD_STATUS_VERSION)
                .addPathSegment("builds")
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repoSlug)
                .addPathSegment("commits")
                .addPathSegment(revisionSha)
                .build();
        bitbucketRequestExecutor.makePostRequest(url, buildStatus);
    }
}
