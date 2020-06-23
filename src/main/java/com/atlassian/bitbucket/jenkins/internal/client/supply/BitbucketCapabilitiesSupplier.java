package com.atlassian.bitbucket.jenkins.internal.client.supply;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.google.common.base.Supplier;
import okhttp3.HttpUrl;

public class BitbucketCapabilitiesSupplier implements Supplier<AtlassianServerCapabilities> {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    public BitbucketCapabilitiesSupplier(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public AtlassianServerCapabilities get() {
        HttpUrl url =
                bitbucketRequestExecutor.getBaseUrl().newBuilder()
                        .addPathSegment("rest")
                        .addPathSegment("capabilities")
                        .build();
        return bitbucketRequestExecutor.makeGetRequest(url, AtlassianServerCapabilities.class).getBody();
    }
}
