package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.RICH_BUILDSTATUS_CAPABILITY_KEY;
import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.WEBHOOK_CAPABILITY_KEY;
import static java.util.Collections.emptySet;
import static okhttp3.HttpUrl.parse;

public class BitbucketCapabilitiesClientImpl implements BitbucketCapabilitiesClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketCapabilitiesClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
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
        HttpUrl url =
                bitbucketRequestExecutor.getBaseUrl().newBuilder()
                        .addPathSegment("rest")
                        .addPathSegment("capabilities")
                        .build();
        return bitbucketRequestExecutor.makeGetRequest(url, AtlassianServerCapabilities.class).getBody();
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
        AtlassianServerCapabilities capabilities =
                new BitbucketCapabilitiesClientImpl(bitbucketRequestExecutor).getServerCapabilities();
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
