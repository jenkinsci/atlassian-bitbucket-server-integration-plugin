package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

public class BitbucketMirroredRepositoryDescriptorClientImpl implements BitbucketMirroredRepositoryDescriptorClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketMirroredRepositoryDescriptorClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public BitbucketPage<BitbucketMirroredRepositoryDescriptor> get(int repositoryId) {
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
