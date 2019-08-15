package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.lang.String.valueOf;

public class GetBasedNextPageFetcher implements NextPageFetcher {

    private final HttpUrl url;
    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    public GetBasedNextPageFetcher(HttpUrl url,
                                   BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.url = url;
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public <T> BitbucketPage<T> next(BitbucketPage<T> previous) {
        if (previous.isLastPage()) {
            throw new IllegalArgumentException("Last page does not have next page");
        }
        return bitbucketRequestExecutor.makeGetRequest(
                nextPageUrl(previous),
                new TypeReference<BitbucketPage<T>>() {}).getBody();
    }

    private HttpUrl nextPageUrl(BitbucketPage previous) {
        return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
    }
}
