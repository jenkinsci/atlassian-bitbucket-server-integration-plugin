package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketPullRequestsClientImpl implements BitbucketPullRequestsClient{

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final HttpUrl url;

    BitbucketPullRequestsClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                               String projectKey,
                               String repoSlug) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(requireNonNull(stripToNull(projectKey), "projectKey"))
                .addPathSegment("repos")
                .addPathSegment(requireNonNull(stripToNull(repoSlug), "repoSlug"))
                .addPathSegment("pull-requests")
                .addQueryParameter("state", "OPEN")
                .addQueryParameter("withAttributes", "false")
                .addQueryParameter("withProperties", "false")
                
                .build();
    }

    @Override
    public Stream<BitbucketPullRequest> getOpenPullRequests() {
        BitbucketPage<BitbucketPullRequest> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketPullRequest> {

        private final HttpUrl url;
        private final BitbucketRequestExecutor bitbucketRequestExecutor;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<BitbucketPullRequest> next(BitbucketPage<BitbucketPullRequest> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketPullRequest> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
