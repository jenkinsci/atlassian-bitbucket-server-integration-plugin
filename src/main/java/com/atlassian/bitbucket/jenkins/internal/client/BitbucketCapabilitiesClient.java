package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;

/**
 * Client to get capabilities from the remote server.
 */
public interface BitbucketCapabilitiesClient extends BitbucketClient<AtlassianServerCapabilities> {

    /**
     * A Client which can be queried for webhook related support.
     *
     * @return webhook client
     * @throws BitbucketMissingCapabilityException thorwn if the capability is missing.
     */
    BitbucketWebhookSupportedEventsClient getWebhookSupportedClient() throws BitbucketMissingCapabilityException;
}
