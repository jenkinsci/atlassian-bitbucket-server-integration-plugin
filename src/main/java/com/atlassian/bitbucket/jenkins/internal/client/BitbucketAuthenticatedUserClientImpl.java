package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import okhttp3.HttpUrl;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.empty;

public class BitbucketAuthenticatedUserClientImpl implements BitbucketAuthenticatedUserClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketAuthenticatedUserClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public Optional<String> getAuthenticatedUser() {
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
