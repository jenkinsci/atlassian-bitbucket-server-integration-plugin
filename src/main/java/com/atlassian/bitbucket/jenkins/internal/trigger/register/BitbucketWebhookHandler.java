package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent;

import java.util.Optional;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNC;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;

public class BitbucketWebhookHandler {

    private final static String CALLBACK_URL_SUFFIX = BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL + "/trigger";

    private final BitbucketCapabilitiesClient serverCapabilities;
    private final BitbucketWebhookClient webhookClient;

    public BitbucketWebhookHandler(
            BitbucketCapabilitiesClient serverCapabilities,
            BitbucketWebhookClient webhookClient) {
        this.serverCapabilities = serverCapabilities;
        this.webhookClient = webhookClient;
    }

    public WebhookRegisterResult register(WebhookRegisterRequest request) {
        checkSupportsWebhooks();
        return isWebhookAlreadyExists(request, webhookClient)
                .map(WebhookRegisterResult::alreadyExists)
                .orElse(WebhookRegisterResult.aSuccess(registerWebhook(request, webhookClient)));
    }

    private BitbucketWebhook registerWebhook(WebhookRegisterRequest request, BitbucketWebhookClient webhookClient) {
        BitbucketWebhookEvent event = getEvent(request);
        return webhookClient.registerWebhook(BitbucketWebhookRequest
                .BitbucketWebhookRequestBuilder.aRequestFor(event.getEventId())
                .withCallbackTo(constructCallbackUrl(request))
                .name(request.getServerId())
                .build());
    }

    private void checkSupportsWebhooks() {
        BitbucketWebhookSupportedEvents events = serverCapabilities.getWebhookSupportedClient().get();
        Set<String> hooks = events.getApplicationWebHooks();
        if (!hooks.contains(REPO_REF_CHANGE.getEventId())) {
            throw new WebhookNotSupportedException("Remote server does not support the required events.");
        }
    }

    private Optional<BitbucketWebhook> isWebhookAlreadyExists(WebhookRegisterRequest request,
                                                              BitbucketWebhookClient webhookClient) {
        BitbucketWebhookEvent event = getEvent(request);
        return webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNC.getEventId())
                .filter(webhook -> constructCallbackUrl(request).equalsIgnoreCase(webhook.getUrl()))
                .filter(hook -> hook.getEvents().contains(event.getEventId()))
                .findAny();
    }

    private BitbucketWebhookEvent getEvent(WebhookRegisterRequest request) {
        return request.isMirror() ? MIRROR_SYNC : REPO_REF_CHANGE;
    }

    private String constructCallbackUrl(WebhookRegisterRequest request) {
        return request.getJenkinsUrl() + CALLBACK_URL_SUFFIX;
    }
}
