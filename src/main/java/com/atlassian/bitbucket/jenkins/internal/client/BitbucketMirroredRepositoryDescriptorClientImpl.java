package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;

public class BitbucketMirroredRepositoryDescriptorClientImpl implements BitbucketMirroredRepositoryDescriptorClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final int repositoryId;

    BitbucketMirroredRepositoryDescriptorClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, int repositoryId) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.repositoryId = repositoryId;
    }

    @Override
    public BitbucketPage<BitbucketMirroredRepositoryDescriptor> getMirroredRepositoryDescriptors() {
        HttpUrl url =
                bitbucketRequestExecutor.getBaseUrl().newBuilder()
                        .addPathSegment("rest")
                        .addPathSegment("mirroring")
                        .addPathSegment("1.0")
                        .addPathSegment("repos")
                        .addPathSegment(String.valueOf(repositoryId))
                        .addPathSegment("mirrors")
                        .build();
        return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketMirroredRepositoryDescriptor>>() {
        }).getBody();
    }
}
