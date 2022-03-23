package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import okhttp3.HttpUrl;

import java.util.concurrent.TimeUnit;

import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.CAPABILITIES_CACHE_DURATION_KEY;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertyUtils.parsePositiveLongFromSystemProperty;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    /**
     * Cache duration for the capabilities response. Defaults to 1 hour in ms.
     */
    public static final long CAPABILITIES_CACHE_DURATION =
            parsePositiveLongFromSystemProperty(CAPABILITIES_CACHE_DURATION_KEY, 360000);
    private final Cache<HttpUrl, AtlassianServerCapabilities> capabilitiesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CAPABILITIES_CACHE_DURATION, TimeUnit.MILLISECONDS)
            .build();
            
    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketClientFactoryImpl(String serverUrl, BitbucketCredentials credentials, ObjectMapper objectMapper,
                               HttpRequestExecutor httpRequestExecutor) {
        bitbucketRequestExecutor = new BitbucketRequestExecutor(serverUrl, httpRequestExecutor, objectMapper,
                credentials);
    }

    @Override
    public BitbucketAuthenticatedUserClient getAuthenticatedUserClient() {
        return new BitbucketAuthenticatedUserClientImpl(bitbucketRequestExecutor);
    }

    @Override
    public BitbucketCapabilitiesClient getCapabilityClient() {
        return new BitbucketCapabilitiesClientImpl(bitbucketRequestExecutor, capabilitiesCache);
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
