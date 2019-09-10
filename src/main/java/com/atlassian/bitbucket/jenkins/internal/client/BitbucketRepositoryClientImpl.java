package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositoryClientImpl implements BitbucketRepositoryClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;

    BitbucketRepositoryClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
    }

    @Override
    public BitbucketRepository get(String repositorySlug) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug);

        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketRepository.class).getBody();
    }
}
