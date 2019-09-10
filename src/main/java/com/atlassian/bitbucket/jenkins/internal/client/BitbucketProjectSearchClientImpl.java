package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import javax.annotation.CheckForNull;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketProjectSearchClientImpl implements BitbucketProjectSearchClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketProjectSearchClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public BitbucketPage<BitbucketProject> get(@CheckForNull String name) {
        if (isBlank(name)) {
            return get(emptyMap());
        }
        return get(singletonMap("name", name));
    }

    private BitbucketPage<BitbucketProject> get(Map<String, String> queryParams) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder().addPathSegment("projects");
        queryParams.forEach(urlBuilder::addQueryParameter);
        HttpUrl url = urlBuilder.build();
        return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketProject>>() {})
                .getBody();
    }
}
