package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.supply.BitbucketCapabilitiesSupplier;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final BitbucketCapabilitiesSupplier capabilitiesSupplier;

    BitbucketClientFactoryImpl(String serverUrl, BitbucketCredentials credentials, ObjectMapper objectMapper,
                               HttpRequestExecutor httpRequestExecutor) {
        bitbucketRequestExecutor = new BitbucketRequestExecutor(serverUrl, httpRequestExecutor, objectMapper,
                credentials);
        capabilitiesSupplier = new BitbucketCapabilitiesSupplier(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketAuthenticatedUserClient getAuthenticatedUserClient() {
        return new BitbucketAuthenticatedUserClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketCapabilitiesClient getCapabilityClient() {
        return new BitbucketCapabilitiesClientImpl(bitbucketRequestExecutor, capabilitiesSupplier);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha) {
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    public BitbucketMirrorClient getMirroredRepositoriesClient(int repositoryId) {
        return new BitbucketMirrorClientImpl(bitbucketRequestExecutor, repositoryId);
    }

    @Override
    public BitbucketProjectClient getProjectClient(String projectKey) {
        return new BitbucketProjectClientImpl(bitbucketRequestExecutor, projectKey);
    }

    @Override
    public BitbucketSearchClient getSearchClient(String projectName) {
        return new BitbucketSearchClientImpl(bitbucketRequestExecutor, projectName);
    }
}
