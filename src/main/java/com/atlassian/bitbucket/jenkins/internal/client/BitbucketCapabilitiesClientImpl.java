package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.supply.BitbucketCapabilitiesSupplier;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.RICH_BUILDSTATUS_CAPABILITY_KEY;
import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.WEBHOOK_CAPABILITY_KEY;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertyUtils.parsePositiveLongFromSystemProperty;
import static java.util.Collections.emptySet;
import static okhttp3.HttpUrl.parse;

public class BitbucketCapabilitiesClientImpl implements BitbucketCapabilitiesClient {

    /**
     * Cache duration for the capabilities response. Defaults to 1 hour in ms.
     */
    public static final long CAPABILITIES_CACHE_DURATION =
            parsePositiveLongFromSystemProperty("bitbucket.client.capabilities.cache.duration", 360000);
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final Supplier<AtlassianServerCapabilities> capabilitiesCache;

    BitbucketCapabilitiesClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, BitbucketCapabilitiesSupplier supplier) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        capabilitiesCache = Suppliers.memoizeWithExpiration(supplier, CAPABILITIES_CACHE_DURATION, TimeUnit.MILLISECONDS);
    }

    @Override
    public BitbucketCICapabilities getCICapabilities() {
        BitbucketCICapabilities ciCapabilities = getCapabilitiesForKey(RICH_BUILDSTATUS_CAPABILITY_KEY, BitbucketCICapabilities.class);
        if (ciCapabilities == null) {
            return new BitbucketCICapabilities(emptySet());
        }
        return ciCapabilities;
    }

    @Override
    public AtlassianServerCapabilities getServerCapabilities() {
        return capabilitiesCache.get();
    }

    @Override
    public BitbucketWebhookSupportedEvents getWebhookSupportedEvents() throws BitbucketMissingCapabilityException {
        BitbucketWebhookSupportedEvents events = getCapabilitiesForKey(WEBHOOK_CAPABILITY_KEY, BitbucketWebhookSupportedEvents.class);
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
