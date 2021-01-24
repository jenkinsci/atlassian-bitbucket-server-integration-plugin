package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositoryClientImpl implements BitbucketRepositoryClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectKey;
    private final String repositorySlug;
    private final HttpUrl url;

    BitbucketRepositoryClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey, String repositorySlug) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repositorySlug = requireNonNull(stripToNull(repositorySlug), "repositorySlug");
        url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(requireNonNull(stripToNull(projectKey), "projectKey"))
                .addPathSegment("repos")
                .addPathSegment(requireNonNull(stripToNull(repositorySlug), "repoSlug"))
                .addPathSegment("pull-requests")
                .addQueryParameter("state", "OPEN")
                .addQueryParameter("withAttributes", "false")
                .addQueryParameter("withProperties", "false")
                .build();
    }

    @Override
    public BitbucketRepository getRepository() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug);

        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketRepository.class).getBody();
    }

    @Override
    public BitbucketWebhookClient getWebhookClient() {
        return new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    @Override
    public Stream<BitbucketPullRequest> getOpenPullRequests() {
        BitbucketPage<BitbucketPullRequest> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new BitbucketRepositoryClientImpl.NextPageFetcherImpl(url, bitbucketRequestExecutor))
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
