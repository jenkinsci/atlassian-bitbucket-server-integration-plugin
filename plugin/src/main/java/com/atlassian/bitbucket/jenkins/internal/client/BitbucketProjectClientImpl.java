package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketProjectClientImpl implements BitbucketProjectClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;

    BitbucketProjectClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
    }

    @Override
    public BitbucketProject getProject() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey);
        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketProject.class).getBody();
    }

    @Override
    public BitbucketRepositoryClient getRepositoryClient(String repositorySlug) {
        return new BitbucketRepositoryClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }
}
