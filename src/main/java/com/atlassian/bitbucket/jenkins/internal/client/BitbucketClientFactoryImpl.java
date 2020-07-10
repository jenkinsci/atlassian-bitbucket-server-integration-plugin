package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.supply.BitbucketCapabilitiesSupplier;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

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

    @VisibleForTesting
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha,
                                                           BitbucketSCMRepository bitbucketSCMRepo,
                                                           BitbucketCICapabilities ciCapabilities,
                                                           InstanceKeyPairProvider instanceKeyPairProvider,
                                                           DisplayURLProvider displayURLProvider) {
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, bitbucketSCMRepo.getProjectKey(),
                    bitbucketSCMRepo.getRepositorySlug(), revisionSha, instanceKeyPairProvider, displayURLProvider);
        }
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha,
                                                           BitbucketSCMRepository bitbucketSCMRepo,
                                                           BitbucketCICapabilities ciCapabilities) {
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, bitbucketSCMRepo.getProjectKey(),
                    bitbucketSCMRepo.getRepositorySlug(), revisionSha);
        }
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
