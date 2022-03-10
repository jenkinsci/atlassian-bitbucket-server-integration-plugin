package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketWebhookClientImpl implements BitbucketWebhookClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final HttpUrl url;

    BitbucketWebhookClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor,
                               String projectKey,
                               String repoSlug) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        url = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(requireNonNull(stripToNull(projectKey), "projectKey"))
                .addPathSegment("repos")
                .addPathSegment(requireNonNull(stripToNull(repoSlug), "repoSlug"))
                .addPathSegment("webhooks")
                .build();
    }

    @Override
    public void deleteWebhook(int webhookId) {
        HttpUrl deleteUrl = url.newBuilder()
                .addPathSegment(String.valueOf(webhookId))
                .build();
        bitbucketRequestExecutor.makeDeleteRequest(deleteUrl);
    }

    @Override
    public BitbucketWebhook updateWebhook(int id, BitbucketWebhookRequest request) {
        HttpUrl updateUrl = url.newBuilder()
                .addPathSegment(String.valueOf(id))
                .build();
        return bitbucketRequestExecutor.makePutRequest(updateUrl, request, BitbucketWebhook.class).getBody();
    }

    @Override
    public Stream<BitbucketWebhook> getWebhooks(String... eventIdFilter) {
        HttpUrl.Builder urlBuilder = url.newBuilder();
        stream(eventIdFilter).forEach(eventId -> urlBuilder.addQueryParameter("event", eventId));
        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketWebhook> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketWebhook>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    @Override
    public BitbucketWebhook registerWebhook(BitbucketWebhookRequest request) {
        return bitbucketRequestExecutor.makePostRequest(
                url,
                request,
                BitbucketWebhook.class).getBody();
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketWebhook> {

        private final HttpUrl url;
        private final BitbucketRequestExecutor bitbucketRequestExecutor;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<BitbucketWebhook> next(BitbucketPage<BitbucketWebhook> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketWebhook>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketWebhook> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
