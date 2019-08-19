package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;

import java.util.stream.Stream;

/**
 * A client to query for and register webhooks in Bitbucket Server.
 */
public interface BitbucketWebhookClient {

    /**
     * Returns a stream of existing webhooks. Result could be further filtered by passing in event id filters.
     * every subsequent fetch of {@link BitbucketPage} results in a remote call to Bitbucket server.
     *
     * @param eventId, Event id filters. These ids are the same as the one recieved as
     *                 {@link com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents}
     * @return a stream of webhooks.
     */
    Stream<BitbucketWebhook> getWebhooks(String... eventId);

    /**
     * Registers the given webhook in the Bitbucket Server.
     *
     * @param request Webhook details
     * @return returns the registered webhook
     */
    BitbucketWebhook registerWebhook(BitbucketWebhookRequest request);
}
