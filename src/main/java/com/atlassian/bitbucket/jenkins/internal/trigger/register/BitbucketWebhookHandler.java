package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNCHRONIZED_EVENT;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class BitbucketWebhookHandler implements WebhookHandler {

    private static final String CALLBACK_URL_SUFFIX = "/" + BIBUCKET_WEBHOOK_URL + "/trigger";
    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookHandler.class.getName());

    private final BitbucketCapabilitiesClient serverCapabilities;
    private final BitbucketWebhookClient webhookClient;

    public BitbucketWebhookHandler(
            BitbucketCapabilitiesClient serverCapabilities,
            BitbucketWebhookClient webhookClient) {
        this.serverCapabilities = serverCapabilities;
        this.webhookClient = webhookClient;
    }

    @Override
    public BitbucketWebhook register(WebhookRegisterRequest request) {
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
                .peek(id -> LOGGER.info("Deleting obsolete webhook" + id))
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
                .filter(hook -> hook.getEvents().contains(toSubscribe.getEventId()))
                .peek(hook -> LOGGER.info("Found an existing webhook - " + hook))
                .findFirst();
    }

    private BitbucketWebhookEvent getEvent(WebhookRegisterRequest request) {
        if (request.isMirror()) {
            try {
                BitbucketWebhookSupportedEvents events = serverCapabilities.getWebhookSupportedClient().get();
                Set<String> hooks = events.getApplicationWebHooks();
                if (hooks.contains(MIRROR_SYNCHRONIZED_EVENT.getEventId())) {
                    return MIRROR_SYNCHRONIZED_EVENT;
                } else if (hooks.contains(REPO_REF_CHANGE.getEventId())) {
                    return REPO_REF_CHANGE;
                } else {
                    throw new WebhookNotSupportedException("Remote server does not support the required events.");
                }
            } catch (BitbucketMissingCapabilityException exception) {
                return REPO_REF_CHANGE;
            }
        }
        return REPO_REF_CHANGE;
    }

    private BitbucketWebhook process(WebhookRegisterRequest request,
                                     BitbucketWebhookEvent event) {
        String callback = constructCallbackUrl(request);
        List<BitbucketWebhook> ownedHooks =
                webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId())
                        .filter(hook -> hook.getName().equals(request.getName()) || hook.getUrl().equals(callback))
                        .collect(toList());
        List<BitbucketWebhook> webhookWithMirrorSync = ownedHooks.stream()
                .filter(hook -> hook.getEvents().contains(MIRROR_SYNCHRONIZED_EVENT.getEventId()))
                .collect(toList());
        List<BitbucketWebhook> webhookWithRepoRefChange = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().contains(REPO_REF_CHANGE.getEventId()))
                .collect(toList());

        if (ownedHooks.size() == 0 ||
            (webhookWithMirrorSync.size() == 0 && event == MIRROR_SYNCHRONIZED_EVENT) ||
            (webhookWithRepoRefChange.size() == 0 && event == REPO_REF_CHANGE)) {
            final BitbucketWebhookRequest webhook = createRequest(request, event);
            LOGGER.info("Registering a new Webhook - " + webhook);
            return webhookClient.registerWebhook(webhook);
        }

        BitbucketWebhook mirrorSyncResult = null;
        BitbucketWebhook repoRefResult = null;

        if (webhookWithMirrorSync.size() > 0) {
            mirrorSyncResult = update(webhookWithMirrorSync, request, MIRROR_SYNCHRONIZED_EVENT);
            webhookWithMirrorSync.remove(mirrorSyncResult);
            deleteWebhooks(webhookWithMirrorSync);
        }

        if (webhookWithRepoRefChange.size() > 0) {
            repoRefResult = update(webhookWithRepoRefChange, request, REPO_REF_CHANGE);
            webhookWithRepoRefChange.remove(repoRefResult);
            deleteWebhooks(webhookWithRepoRefChange);
        }

        if (mirrorSyncResult != null && mirrorSyncResult.getEvents().contains(event.getEventId())) {
            return mirrorSyncResult;
        } else {
            return repoRefResult;
        }
    }

    private BitbucketWebhook update(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                    BitbucketWebhookEvent toSubscribe) {
        return findSame(webhooks, request, toSubscribe)
                .orElseGet(() -> updateRemoteWebhook(webhooks.get(0), request, toSubscribe));
    }

    private BitbucketWebhook updateRemoteWebhook(BitbucketWebhook existing, WebhookRegisterRequest request,
                                                 BitbucketWebhookEvent toSubscribe) {
        BitbucketWebhookRequest r = createRequest(request, toSubscribe);
        BitbucketWebhook updated = webhookClient.updateWebhook(existing.getId(), r);
        LOGGER.info(format("Updating exising webhook - %s with new webhook %s", existing, r));
        return updated;
    }
}
