package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketDeploymentClientImpl implements BitbucketDeploymentClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repoSlug;
    private final String revisionSha;

    public BitbucketDeploymentClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey,
                                         String repoSlug, String revisionSha) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repoSlug = requireNonNull(stripToNull(repoSlug), "repoSlug");
        this.revisionSha = requireNonNull(stripToNull(revisionSha), "revisionSha");
    }

    @Override
    public void post(BitbucketDeployment deployment) {
        HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment("1.0")
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repoSlug)
                .addPathSegment("commits")
                .addPathSegment(revisionSha)
                .addPathSegment("deployments")
                .build();

        bitbucketRequestExecutor.makePostRequest(url, deployment);
    }
}
