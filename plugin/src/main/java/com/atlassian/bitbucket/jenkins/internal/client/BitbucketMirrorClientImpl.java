package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketMirrorClientImpl implements BitbucketMirrorClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final int repositoryId;

    BitbucketMirrorClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                              int repositoryId) {
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
        return bitbucketRequestExecutor.makeGetRequest(url,
                new TypeReference<BitbucketPage<BitbucketMirroredRepositoryDescriptor>>() {}).getBody();
    }

    @Override
    public BitbucketMirroredRepository getRepositoryDetails(
            BitbucketMirroredRepositoryDescriptor repositoryDescriptor) {
        String repoUrl = repositoryDescriptor.getSelfLink();
        if (isEmpty(repoUrl)) {
            throw new BitbucketClientException("Empty Repo URL");
        }

        HttpUrl mirrorUrl = HttpUrl.parse(repoUrl);
        if (mirrorUrl == null) {
            throw new BitbucketClientException("Invalid repo URL " + repoUrl);
        }

        return bitbucketRequestExecutor.makeGetRequest(mirrorUrl, BitbucketMirroredRepository.class).getBody();
    }
}
