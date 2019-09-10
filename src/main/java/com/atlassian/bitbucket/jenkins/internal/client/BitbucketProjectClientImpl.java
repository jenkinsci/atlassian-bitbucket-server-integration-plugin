package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import okhttp3.HttpUrl;

public class BitbucketProjectClientImpl implements BitbucketProjectClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketProjectClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public BitbucketProject get(String projectKey) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey);
        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketProject.class).getBody();
    }
}
