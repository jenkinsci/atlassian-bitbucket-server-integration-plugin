package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import okhttp3.HttpUrl;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.empty;

public class BitbucketUsernameClientImpl implements BitbucketUsernameClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketUsernameClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public Optional<String> get() {
        HttpUrl url =
                bitbucketRequestExecutor.getBaseUrl().newBuilder()
                        .addPathSegment("rest")
                        .addPathSegment("capabilities")
                        .build();
        BitbucketResponse<AtlassianServerCapabilities> response =
                bitbucketRequestExecutor.makeGetRequest(url, AtlassianServerCapabilities.class);
        List<String> usernames = response.getHeaders().get("X-AUSERNAME");
        if (usernames != null) {
            return usernames.stream().findFirst();
        }
        return empty();
    }
}
