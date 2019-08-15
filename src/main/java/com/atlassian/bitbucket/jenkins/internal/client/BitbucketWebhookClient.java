package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;

/**
 * A client to query for and register web hooks in Bitbucket Server.
 */
public interface BitbucketWebhookClient {

    /**
     * Fetch existing webhooks. Result could be further filtered by passing in event id filters.
     *
     * @param eventIdFilter, Event id filters. These ids are the same as the one recieved as
     *                       {@link com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents}
     * @return a page of webhooks.
     */
    BitbucketPage<BitbucketWebhook> getWebhooks(String... eventIdFilter);

    /**
     * Registers the given webhook in the Bitbucket Server.
     *
     * @param request Webhook details
     * @return returns the registered webhook
     */
    BitbucketWebhook registerWebhook(WebhookRegisterRequest request);
}
