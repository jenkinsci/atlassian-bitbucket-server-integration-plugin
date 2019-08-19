package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.util.Arrays.stream;

public class BitbucketWebhookClientImpl implements BitbucketWebhookClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final HttpUrl url;

    public BitbucketWebhookClientImpl(String projectKey,
                                      String repoSlug,
                                      BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.url = getWebhookUrl(projectKey, repoSlug);
    }

    @Override
    public BitbucketPage<BitbucketWebhook> getWebhooks(String... eventIdFilter) {
        HttpUrl.Builder urlBuilder = url.newBuilder();
        stream(eventIdFilter).forEach(eventId -> urlBuilder.addQueryParameter("event", eventId));
        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(),
                new TypeReference<BitbucketPage<BitbucketWebhook>>() {}).getBody();
    }

    @Override
    public BitbucketWebhook registerWebhook(BitbucketWebhookRequest request) {
        HttpUrl.Builder urlBuilder = url.newBuilder();
        return bitbucketRequestExecutor.makePostRequest(
                urlBuilder.build(),
                request,
                BitbucketWebhook.class).getBody();
    }

    private HttpUrl getWebhookUrl(String projectSlug, String repoSlug) {
        return bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectSlug)
                .addPathSegment("repos")
                .addPathSegment(repoSlug)
                .addPathSegment("webhooks")
                .build();
    }
}
