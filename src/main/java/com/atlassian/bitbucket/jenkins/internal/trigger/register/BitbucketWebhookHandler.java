package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * The following assumptions is made while handling webhooks,
 * 1. Separate webhooks will be added for repo ref, pull request opened events and mirror sync events
 * 2. Input name is unique across all jenkins instance and will not shared by any system. Wrong URL with the given name
 * will be corrected.
 * 3. The callback URL is unique to this instance. Wrong name for given callback will be corrected.
 *
 * Webhook handling is done in following ways,
 *
 * 1. If there are no webhooks in the system, a new webhook is registered
 * 2. Existing webhooks are modified to reflect correct properties of webhooks.
 */
public class BitbucketWebhookHandler implements WebhookHandler {

    private static final String CALLBACK_URL_SUFFIX = BIBUCKET_WEBHOOK_URL + "/trigger";
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
        Collection<BitbucketWebhookEvent> events = getEvents(request);
        return process(request, events);
    }

    private String constructCallbackUrl(WebhookRegisterRequest request) {
        String jenkinsUrl = request.getJenkinsUrl();
        StringBuilder url = new StringBuilder(request.getJenkinsUrl());
        url = jenkinsUrl.endsWith("/") ? url : url.append("/");
        return url.append(CALLBACK_URL_SUFFIX).toString();
    }

    private BitbucketWebhookRequest createRequest(WebhookRegisterRequest request,
                                                  Collection<BitbucketWebhookEvent> events) {
        return BitbucketWebhookRequest.Builder.aRequestFor(events.stream().map(BitbucketWebhookEvent::getEventId)
                .collect(Collectors.toSet()))
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
                                                Collection<BitbucketWebhookEvent> toSubscribe) {
        String callback = constructCallbackUrl(request);
        return webhooks
                .stream()
                .filter(hook -> hook.getName().equals(request.getName()))
                .filter(hook -> hook.getUrl().equals(callback))
                .filter(BitbucketWebhookRequest::isActive)
                .filter(hook -> hook.getEvents().containsAll(toSubscribe.stream()
                        .map(BitbucketWebhookEvent::getEventId).collect(Collectors.toSet())))
                .peek(hook -> LOGGER.info("Found an existing webhook - " + hook))
                .findFirst();
    }

    /**
     * Returns the correct webhook event to subscribe to.
     * If webhook capability contains pull request opened event, subscribe to pr opened (regardless of mirror or ref change)
     * For Mirror sync event, the input request should point to mirror.
     * For Repo ref event,
     * 1. Input request does not point to mirrors
     * 2. If webhook capability does not contains mirror sync, we still subscribe to repo ref.
     *
     * If the webhook capability is not there, we still subscribe to repo ref event and pull request opened event.
     *
     * @param request the input request
     * @return the correct webhook event
     */
    private Collection<BitbucketWebhookEvent> getEvents(WebhookRegisterRequest request) {
        Collection<BitbucketWebhookEvent> supportedEvents = new HashSet<>();
        if (request.isMirror()) {
            try {
                BitbucketWebhookSupportedEvents events = serverCapabilities.getWebhookSupportedEvents();
                Set<String> hooks = events.getApplicationWebHooks();
                if (hooks.contains(MIRROR_SYNCHRONIZED_EVENT.getEventId())) {
                    supportedEvents.add(MIRROR_SYNCHRONIZED_EVENT);
                } else if (hooks.contains(REPO_REF_CHANGE.getEventId())) {
                    supportedEvents.add(REPO_REF_CHANGE);
                }
            } catch (BitbucketMissingCapabilityException exception) { //version doesn't support webhooks but support ref change & pr
                supportedEvents.add(REPO_REF_CHANGE);
            }
        } else {
            if (request.isTriggerOnPush()) {
                supportedEvents.add(REPO_REF_CHANGE);
            }
            if (request.isTriggerOnPR()) {
                supportedEvents.add(PULL_REQUEST_OPENED_EVENT);
            }
        }
        if (supportedEvents.isEmpty()) {
            throw new WebhookNotSupportedException("Remote server does not support the required events.");
        }
        return supportedEvents;
    }

    private BitbucketWebhook process(WebhookRegisterRequest request,
                                     Collection<BitbucketWebhookEvent> events) {
        String callback = constructCallbackUrl(request);
        List<BitbucketWebhook> ownedHooks =
                webhookClient.getWebhooks(REPO_REF_CHANGE.getEventId(), MIRROR_SYNCHRONIZED_EVENT.getEventId(),
                        PULL_REQUEST_OPENED_EVENT.getEventId())
                        .filter(hook -> hook.getName().equals(request.getName()) || hook.getUrl().equals(callback))
                        .collect(toList());
        List<BitbucketWebhook> webhookWithMirrorSync = ownedHooks.stream()
                .filter(hook -> hook.getEvents().contains(MIRROR_SYNCHRONIZED_EVENT.getEventId()))
                .collect(toList());
        List<BitbucketWebhook> webhookWithRepoRefChangeOnly = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().equals(Collections.singleton(REPO_REF_CHANGE.getEventId())))
                .collect(toList());
        List<BitbucketWebhook> webhookWithPROnly = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().equals(Collections.singleton(PULL_REQUEST_OPENED_EVENT.getEventId())))
                .collect(toList());

        Set<String> refAndPullEvent = new HashSet<>(Arrays.asList(REPO_REF_CHANGE.getEventId(), PULL_REQUEST_OPENED_EVENT.getEventId()));

        List<BitbucketWebhook> webhookWithRepoRefChangeOrPR = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().stream().anyMatch(refAndPullEvent::contains))
                .collect(toList());

        if (ownedHooks.isEmpty() ||
            (webhookWithMirrorSync.isEmpty() && events.contains(MIRROR_SYNCHRONIZED_EVENT)) ||
            (webhookWithRepoRefChangeOrPR.isEmpty() && (events.contains(PULL_REQUEST_OPENED_EVENT) || events.contains(REPO_REF_CHANGE)))) {
            BitbucketWebhookRequest webhook = createRequest(request, events);
            BitbucketWebhook result = webhookClient.registerWebhook(webhook);
            LOGGER.info("New Webhook registered - " + result);
            return result;
        }

        BitbucketWebhook mirrorSyncResult =
                handleExistingWebhook(request, webhookWithMirrorSync, Collections.singleton(MIRROR_SYNCHRONIZED_EVENT));

        BitbucketWebhook repoResult = null;
        Collection<BitbucketWebhookEvent> supportedEvents = new HashSet<>();
        if (events.contains(REPO_REF_CHANGE) && events.contains(PULL_REQUEST_OPENED_EVENT)) {
            repoResult =
                    handleExistingWebhook(request, webhookWithRepoRefChangeOrPR, events);
        } else if (!webhookWithRepoRefChangeOnly.isEmpty() && events.contains(REPO_REF_CHANGE)) {
            repoResult = handleExistingWebhook(request, webhookWithRepoRefChangeOnly, events);
        } else if (!webhookWithPROnly.isEmpty() && events.contains(PULL_REQUEST_OPENED_EVENT)) {
            repoResult = handleExistingWebhook(request, webhookWithPROnly, events);
        } else {
            supportedEvents.add(REPO_REF_CHANGE);
            supportedEvents.add(PULL_REQUEST_OPENED_EVENT);
            repoResult = handleExistingWebhook(request, webhookWithRepoRefChangeOrPR, supportedEvents);

        }

        if (mirrorSyncResult != null &&
            events.stream().anyMatch(event -> mirrorSyncResult.getEvents().contains(event.getEventId()))) {
            return mirrorSyncResult;
        } else {
            return repoResult;
        }
    }

    @Nullable
    private BitbucketWebhook handleExistingWebhook(WebhookRegisterRequest request,
                                                   List<BitbucketWebhook> existingWebhooks,
                                                   Collection<BitbucketWebhookEvent> toSubscribe) {
        BitbucketWebhook result = null;
        if (!existingWebhooks.isEmpty()) {
           // result = update(existingWebhooks, request, toSubscribe);
            result = findSame(existingWebhooks, request, toSubscribe)
                    .orElseGet(() -> updateRemoteWebhook(existingWebhooks.get(0), request, toSubscribe));
            existingWebhooks.remove(result);
            deleteWebhooks(existingWebhooks);
        }
        return result;
    }

    private BitbucketWebhook update(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                    Collection<BitbucketWebhookEvent> toSubscribe) {
        return findSame(webhooks, request, toSubscribe)
                .orElseGet(() -> updateRemoteWebhook(webhooks.get(0), request, toSubscribe));
    }

    private BitbucketWebhook updateRemoteWebhook(BitbucketWebhook existing, WebhookRegisterRequest request,
                                                 Collection<BitbucketWebhookEvent> toSubscribe) {
        BitbucketWebhookRequest r = createRequest(request, toSubscribe);
        BitbucketWebhook updated = webhookClient.updateWebhook(existing.getId(), r);
        LOGGER.info(format("Exising webhook updtated - %s with new webhook %s", existing, r));
        return updated;
    }
}
