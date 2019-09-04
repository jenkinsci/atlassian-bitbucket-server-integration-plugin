package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNCHRONIZED_EVENT;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static java.util.stream.Collectors.toList;

public class BitbucketWebhookHandler implements WebhookHandler {

    private static final String CALLBACK_URL_SUFFIX = "/" + BIBUCKET_WEBHOOK_URL + "/trigger";

    private final BitbucketCapabilitiesClient serverCapabilities;
    private final BitbucketWebhookClient webhookClient;

    public BitbucketWebhookHandler(
            BitbucketCapabilitiesClient serverCapabilities,
            BitbucketWebhookClient webhookClient) {
        this.serverCapabilities = serverCapabilities;
        this.webhookClient = webhookClient;
    }

    @Override
    public WebhookRegisterResult register(WebhookRegisterRequest request) {
        BitbucketWebhookEvent event = getEvent(request);
        return process(request, event);
    }

    private String constructCallbackUrl(WebhookRegisterRequest request) {
        String jenkinsUrl = request.getJenkinsUrl();
        if (jenkinsUrl.endsWith("/")) {
            jenkinsUrl = jenkinsUrl.substring(0, jenkinsUrl.length() - 1);
        }
        return jenkinsUrl + CALLBACK_URL_SUFFIX;
    }

    private BitbucketWebhookRequest createRequest(WebhookRegisterRequest request, BitbucketWebhookEvent event) {
        return BitbucketWebhookRequest.Builder.aRequestFor(event.getEventId())
                .withCallbackTo(constructCallbackUrl(request))
                .name(request.getName())
                .build();
    }

    private void deleteWebhooks(List<BitbucketWebhook> webhooks) {
        webhooks.stream()
                .map(BitbucketWebhook::getId)
                .forEach(webhookClient::deleteWebhook);
    }

    private Optional<BitbucketWebhook> findSame(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                                BitbucketWebhookEvent toSubscribe) {
        String callback = constructCallbackUrl(request);
        return webhooks
                .stream()
                .filter(hook -> hook.getName().equals(request.getName()))
                .filter(hook -> hook.getUrl().equals(callback))
                .filter(BitbucketWebhookRequest::isActive)
                .filter(hook -> hook.getEvents().size() == 1)
                .filter(hook -> hook.getEvents().contains(toSubscribe))
                .findFirst();
    }

    private BitbucketWebhookEvent getEvent(WebhookRegisterRequest request) {
        if (request.isMirror()) {
            BitbucketWebhookSupportedEvents events = serverCapabilities.getWebhookSupportedClient().get();
            Set<String> hooks = events.getApplicationWebHooks();
            if (hooks.contains(MIRROR_SYNCHRONIZED_EVENT.getEventId())) {
                return MIRROR_SYNCHRONIZED_EVENT;
            } else if (!hooks.contains(REPO_REF_CHANGE.getEventId())) {
                throw new WebhookNotSupportedException("Remote server does not support the required events.");
            } else {
                return REPO_REF_CHANGE;
            }
        }
        return REPO_REF_CHANGE;
    }

    private WebhookRegisterResult process(WebhookRegisterRequest request,
                                          BitbucketWebhookEvent event) {
        String callback = constructCallbackUrl(request);
        List<BitbucketWebhook> ownedHooks =
                webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())
                        .filter(hook -> hook.getName().equals(request.getName()) || hook.getUrl().equals(callback))
                        .collect(toList());
        if (ownedHooks.size() == 0) {
            return WebhookRegisterResult.aSuccess(webhookClient.registerWebhook(createRequest(request, event)));
        }

        BitbucketWebhook result;
        List<BitbucketWebhook> webhookWithMirrorSync = ownedHooks.stream()
                .filter(hook -> hook.getEvents().contains(MIRROR_SYNCHRONIZED_EVENT.getEventId()))
                .collect(toList());
        if (webhookWithMirrorSync.size() > 0) {
            result = update(webhookWithMirrorSync, request, MIRROR_SYNCHRONIZED_EVENT);
            ownedHooks.remove(result);
        } else {
            result = update(ownedHooks, request, event);
        }
        ownedHooks.remove(result);
        deleteWebhooks(ownedHooks);
        return WebhookRegisterResult.alreadyExists(result);
    }

    private BitbucketWebhook update(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                    BitbucketWebhookEvent toSubscribe) {
        return findSame(webhooks, request, toSubscribe)
                .orElseGet(() -> webhookClient.updateWebhook(webhooks.get(0).getId(), createRequest(request, toSubscribe)));
    }
}
