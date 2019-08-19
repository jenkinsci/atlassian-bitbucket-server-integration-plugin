package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Arrays.stream;

public class BitbucketWebhookClientImpl implements BitbucketWebhookClient {

    private final String projectKey;
    private final String repoSlug;
    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    public BitbucketWebhookClientImpl(String projectKey,
                                      String repoSlug,
                                      BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.projectKey = projectKey;
        this.repoSlug = repoSlug;
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    @Override
    public Stream<BitbucketWebhook> getWebhooks(String... eventIdFilter) {
        HttpUrl.Builder urlBuilder = getWebhookUrl(projectKey, repoSlug);
        stream(eventIdFilter).forEach(eventId -> urlBuilder.addQueryParameter("event", eventId));
        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketWebhook> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketWebhook>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    @Override
    public BitbucketWebhook registerWebhook(WebhookRegisterRequest request) {
        return bitbucketRequestExecutor.makePostRequest(
                getWebhookUrl(projectKey, repoSlug).build(),
                request.getRequestPayload(),
                BitbucketWebhook.class).getBody();
    }

    private HttpUrl.Builder getWebhookUrl(String projectSlug, String repoSlug) {
        return bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectSlug)
                .addPathSegment("repos")
                .addPathSegment(repoSlug)
                .addPathSegment("webhooks");
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

        private HttpUrl nextPageUrl(BitbucketPage previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}
