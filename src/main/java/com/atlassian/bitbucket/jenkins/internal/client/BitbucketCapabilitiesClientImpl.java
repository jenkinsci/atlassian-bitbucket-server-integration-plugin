package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.supply.BitbucketCapabilitiesSupplier;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentCapabilities;
import com.google.common.util.concurrent.UncheckedExecutionException;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.*;

import com.google.common.cache.Cache;

import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptySet;
import static okhttp3.HttpUrl.parse;

public class BitbucketCapabilitiesClientImpl implements BitbucketCapabilitiesClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final Cache<HttpUrl, AtlassianServerCapabilities> capabilitiesCache;

    BitbucketCapabilitiesClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, Cache capabilitiesCache) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.capabilitiesCache = capabilitiesCache;
    }

    @Override
    public BitbucketCICapabilities getCICapabilities() {
        BitbucketCICapabilities ciCapabilities =
                getCapabilitiesForKey(RICH_BUILDSTATUS_CAPABILITY_KEY, BitbucketCICapabilities.class);
        if (ciCapabilities == null) {
            return new BitbucketCICapabilities(emptySet());
        }
        return ciCapabilities;
    }

    @Override
    public BitbucketDeploymentCapabilities getDeploymentCapabilities() {
        BitbucketDeploymentCapabilities deploymentCapabilities = getCapabilitiesForKey(DEPLOYMENTS_CAPABILITY_KEY,
                BitbucketDeploymentCapabilities.class);
        if (deploymentCapabilities == null) {
            return new BitbucketDeploymentCapabilities(false);
        }
        return deploymentCapabilities;
    }

    @Override
    public AtlassianServerCapabilities getServerCapabilities() {
        try {
            return capabilitiesCache.get(bitbucketRequestExecutor.getBaseUrl(), () ->
                    new BitbucketCapabilitiesSupplier(bitbucketRequestExecutor).get()
            );
        } catch (ExecutionException executionException) {
            throw new RuntimeException(executionException);
        } catch (UncheckedExecutionException uncheckedExecutionException) {
            // We unwrap the exception in case consumers have handling for specific exception cases
            throw (RuntimeException) uncheckedExecutionException.getCause();
        }
    }

    @Override
    public BitbucketWebhookSupportedEvents getWebhookSupportedEvents() throws BitbucketMissingCapabilityException {
        BitbucketWebhookSupportedEvents events =
                getCapabilitiesForKey(WEBHOOK_CAPABILITY_KEY, BitbucketWebhookSupportedEvents.class);
        if (events == null) {
            throw new BitbucketMissingCapabilityException(
                    "Remote Bitbucket Server does not support Webhooks. Make sure " +
                    "Bitbucket server supports webhooks or correct version of it is installed.");
        }
        return events;
    }

    @Nullable
    private <T> T getCapabilitiesForKey(String key, Class<T> returnType) {
        AtlassianServerCapabilities capabilities = getServerCapabilities();
        String urlStr = capabilities.getCapabilities().get(key);
        if (urlStr == null) {
            return null;
        }

        HttpUrl url = parse(urlStr);
        if (url == null) {
            throw new IllegalStateException(
                    "URL to fetch supported webhook supported event is wrong. URL: " + urlStr);
        }
        return bitbucketRequestExecutor.makeGetRequest(url, returnType).getBody();
    }
}
