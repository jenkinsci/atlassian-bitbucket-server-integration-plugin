package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketWebhookClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookSupportedEvents;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.*;
import static java.lang.String.format;

/**
 * The following assumptions is made while handling webhooks,
 * 1. A single webhook will be used for all events
 * 2. Input name and url is unique across all jenkins instance and will not shared by any system. Difference in URL only
 * is treated as a separate webook and will not be updated nor deleted.
 *
 * Webhook handling is done in following ways,
 *
 * 1. If there are no webhooks in the system, a new webhook is registered
 * 2. Existing webhook is modified to reflect correct properties of webhooks.
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
    @Nullable
    public BitbucketWebhook register(WebhookRegisterRequest request) {
        Collection<BitbucketWebhookEvent> events = getEvents(request);
        //If wanted webhook has no events, do nothing
        if (events.isEmpty()) {
            return null;
        }
        return process(request, events);
    }

    private String constructCallbackUrl(WebhookRegisterRequest request) {
        String jenkinsUrl = request.getJenkinsUrl();
        StringBuilder url = new StringBuilder(request.getJenkinsUrl());
        if (!jenkinsUrl.endsWith("/")) {
            url.append("/");
        }
        return url.append(CALLBACK_URL_SUFFIX).toString();
    }

    private BitbucketWebhookRequest createRequest(WebhookRegisterRequest request,
                                                  Collection<BitbucketWebhookEvent> events) {
        return new BitbucketWebhookRequest.Builder(events.stream().map(BitbucketWebhookEvent::getEventId)
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

    /**
     * Returns the webhook events to subscribe to.
     * For Mirror sync event, the input request should point to mirror.
     * If the webhook capability does not contain mirror sync, this version with webhooks at least supports ref change,
     * since this version supports webhooks, it will definitely support pull requests and ref changes
     * We subscribe to events depending on if we want to only listen for ref changes, only pull request events or both
     * If we subscribe to pull request events, we subscribe to all of these: pull requests are opened,
     * closed, deleted or merged.
     *
     * @param request the input request
     * @return the correct webhook event
     */
    private Collection<BitbucketWebhookEvent> getEvents(WebhookRegisterRequest request) {
        Set<BitbucketWebhookEvent> supportedEvents = new HashSet<>();
        Set<BitbucketWebhookEvent> forbiddenEvents = new HashSet<>();
        Set<String> hooks = new HashSet<>();
        try {
            BitbucketWebhookSupportedEvents events = serverCapabilities.getWebhookSupportedEvents();
            hooks.addAll(events.getApplicationWebHooks());
        } catch (BitbucketMissingCapabilityException e) {
            forbiddenEvents.add(MIRROR_SYNCHRONIZED);
            forbiddenEvents.add(PULL_REQUEST_FROM_REF_UPDATED);
        }

        if (request.isMirror() && hooks.contains(MIRROR_SYNCHRONIZED.getEventId())) {
            supportedEvents.add(MIRROR_SYNCHRONIZED);
        } else if (request.isTriggerOnRefChange()) {
            supportedEvents.add(REPO_REF_CHANGE);
        }
        if (request.isTriggerOnPullRequest()) {
            supportedEvents.add(PULL_REQUEST_DECLINED);
            supportedEvents.add(PULL_REQUEST_DELETED);
            if (!forbiddenEvents.contains(PULL_REQUEST_FROM_REF_UPDATED) && !request.isMirror()) {
                supportedEvents.add(PULL_REQUEST_FROM_REF_UPDATED);
            }
            supportedEvents.add(PULL_REQUEST_MERGED);
            supportedEvents.add(PULL_REQUEST_OPENED);
        }

        return supportedEvents;
    }

    /**
     * Returns the Bitbucket webhook we have just registered/updated.
     * There should only be one webhook for each Jenkins to Bitbucket connection.
     * Possible webhook configurations: mirror sync, ref change, pull request events, ref change.
     * The following is a summary of what this method does:
     * 1. Registering new webhooks: This only happens when there is no webhook
     * 2. Updating existing webhooks:
     * The events on the existing webhook will be retained and any new events are added to it.
     * It will not *remove* existing events from the webhook, only add new ones.
     *
     * @param request the input request
     * @param events
     * @return the registered/updated bitbucket webhook
     */
    private BitbucketWebhook process(WebhookRegisterRequest request,
                                     Collection<BitbucketWebhookEvent> events) {
        String callback = constructCallbackUrl(request);
        List<BitbucketWebhook> webhooks = webhookClient.getWebhooks().collect(Collectors.toList());
        Set<BitbucketWebhook> serverSideWebhooks =
                webhooks.stream()
                        .filter(hook -> hook.getName().equals(request.getName()) && hook.getUrl().equals(callback))
                        .collect(Collectors.toSet());

        Set<BitbucketWebhookEvent> desiredEvents = new HashSet<>(events);
        Set<BitbucketWebhookEvent> serverSideWebhookEvents = serverSideWebhooks.stream().flatMap(event ->
                event.getEvents().stream().map(BitbucketWebhookEvent::findByEventId)).collect(Collectors.toSet());
        desiredEvents.addAll(serverSideWebhookEvents);

        if (serverSideWebhookEvents.containsAll(desiredEvents) &&
                serverSideWebhookEvents.size() == desiredEvents.size()) {
            //check that the webhooks are actually active and not just registered by disabled.
            Optional<BitbucketWebhook> foundWebook = serverSideWebhooks.stream().filter(event -> event.isActive() &&
                    callback.equalsIgnoreCase(event.getUrl()))
                    .findFirst();
            if (foundWebook.isPresent()) {
                return foundWebook.get();
            }
        }

        //Determining whether to create a new webhook
        //We will handle mirror syncs separate from all other events.
        // If no webhook with wanted events exist, we register a new one.
        if (serverSideWebhooks.isEmpty()) {
            BitbucketWebhookRequest webhook = createRequest(request, desiredEvents);
            BitbucketWebhook result = webhookClient.registerWebhook(webhook);
            LOGGER.info("New Webhook registered - " + result);
            return result;
        }

        return update(webhooks, request, desiredEvents);
    }

    private BitbucketWebhook update(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                    Collection<BitbucketWebhookEvent> toSubscribe) {
        if (!webhooks.isEmpty()) {
            BitbucketWebhook webhook = updateRemoteWebhook(webhooks.get(0), request, toSubscribe);
            // We remove all other matching webhooks other than the first, so there are no duplicates
            if (webhooks.size() > 1) {
                deleteWebhooks(webhooks.subList(1, webhooks.size()));
            }
            return webhook;
        }
        throw new IllegalArgumentException("Empty list of webhooks provided, need at least one to update");
    }

    private BitbucketWebhook updateRemoteWebhook(BitbucketWebhook existing, WebhookRegisterRequest request,
                                                 Collection<BitbucketWebhookEvent> toSubscribe) {
        BitbucketWebhookRequest r = createRequest(request, toSubscribe);
        BitbucketWebhook updated = webhookClient.updateWebhook(existing.getId(), r);
        LOGGER.info(format("Existing webhook updated - %s with new webhook %s", existing, r));
        return updated;
    }
}
