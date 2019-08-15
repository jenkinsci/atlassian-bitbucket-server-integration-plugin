package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;

import java.util.stream.Stream;

/**
 * A client to query for and register web hooks in Bitbucket Server.
 */
public interface BitbucketWebhookClient {

    /**
     * Returns a stream of existing webhooks. Result could be further filtered by passing in event id filters.
     * every subsequent fetch of {@link BitbucketPage} results in a remote call to Bitbucket server.
     *
     * @param eventIdFilter, Event id filters. These ids are the same as the one recieved as
     *                       {@link com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents}
     * @return a stream of pages of webhooks.
     */
    Stream<BitbucketPage<BitbucketWebhook>> getWebhooks(String... eventIdFilter);

    /**
     * Registers the given webhook in the Bitbucket Server.
     *
     * @param request Webhook details
     * @return returns the registered webhook
     */
    BitbucketWebhook registerWebhook(WebhookRegisterRequest request);
}
