package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.util.Arrays.stream;

public class BitbucketWebhookClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    public BitbucketWebhookClient(BitbucketRequestExecutor bitbucketRequestExecutor) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
    }

    public BitbucketPage<BitbucketWebhook> getWebhooks(String projectSlug, String repoSlug, String... eventIdFilter) {
        HttpUrl.Builder urlBuilder = getWebhookUrl(projectSlug, repoSlug);
        stream(eventIdFilter).forEach(eventId -> urlBuilder.addQueryParameter("event", eventId));
        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), new TypeReference<BitbucketPage<BitbucketWebhook>>() {}).getBody();
    }

    public BitbucketWebhook registerWebhook(WebhookRegisterRequest request) {
        return bitbucketRequestExecutor.makePostRequest(
                getWebhookUrl(request.getProjectSlug(), request.getRepoSlug()).build(),
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
}
