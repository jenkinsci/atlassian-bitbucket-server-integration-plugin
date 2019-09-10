package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketClientFactoryImpl(String serverUrl, BitbucketCredentials credentials, ObjectMapper objectMapper,
                               HttpRequestExecutor httpRequestExecutor) {
        bitbucketRequestExecutor = new BitbucketRequestExecutor(serverUrl, httpRequestExecutor, objectMapper,
                credentials);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient() {
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketCapabilitiesClient getCapabilityClient() {
        return new BitbucketCapabilitiesClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketMirroredRepositoryDescriptorClient getMirroredRepositoriesClient() {
        return new BitbucketMirroredRepositoryDescriptorClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketProjectClient getProjectClient() {
        return new BitbucketProjectClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketProjectSearchClient getProjectSearchClient() {
        return new BitbucketProjectSearchClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketRepositoryClient getRepositoryClient(String projectKey) {
        return new BitbucketRepositoryClientImpl(bitbucketRequestExecutor, projectKey);
    }

    @Override
    public BitbucketRepositorySearchClient getRepositorySearchClient(String projectName) {
        return new BitbucketRepositorySearchClientImpl(bitbucketRequestExecutor, projectName);
    }

    @Override
    public BitbucketUsernameClient getUsernameClient() {
        return new BitbucketUsernameClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketWebhookClient getWebhookClient(String projectKey, String repositorySlug) {
        return new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }
}
