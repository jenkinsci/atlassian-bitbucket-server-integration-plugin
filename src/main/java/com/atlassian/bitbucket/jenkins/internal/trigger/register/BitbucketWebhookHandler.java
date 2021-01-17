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
    private static final Collection<String> AllPREventIds = new HashSet<>();
    private static final Collection<BitbucketWebhookEvent> allPREvents = Arrays.asList(PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT);
    private static final Set<String> refAndPREventIds = new HashSet<>();
    private final BitbucketCapabilitiesClient serverCapabilities;
    private final BitbucketWebhookClient webhookClient;

    static {
        AllPREventIds.addAll(PULL_REQUEST_OPENED_EVENT.getEventIds());
        AllPREventIds.addAll(PULL_REQUEST_CLOSED_EVENT.getEventIds());
        refAndPREventIds.addAll(REPO_REF_CHANGE.getEventIds());
        refAndPREventIds.addAll(PULL_REQUEST_OPENED_EVENT.getEventIds());
    }

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
        return BitbucketWebhookRequest.Builder.aRequestFor(events.stream().flatMap(bitbucketWebhookEvent -> bitbucketWebhookEvent.getEventIds().stream())
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

    //TODO: find same needs to change
    private Optional<BitbucketWebhook> findSame(List<BitbucketWebhook> webhooks, WebhookRegisterRequest request,
                                                Collection<BitbucketWebhookEvent> toSubscribe) {
        String callback = constructCallbackUrl(request);
        return webhooks
                .stream()
                .filter(hook -> hook.getName().equals(request.getName()))
                .filter(hook -> hook.getUrl().equals(callback))
                .filter(BitbucketWebhookRequest::isActive)
                .filter(hook -> hook.getEvents().containsAll(toSubscribe.stream()
                        .flatMap(bitbucketWebhookEvent -> bitbucketWebhookEvent.getEventIds().stream()).collect(Collectors.toSet())))
                .peek(hook -> LOGGER.info("Found an existing webhook - " + hook))
                .findFirst();
    }

    /**
     * Returns the correct webhook event to subscribe to.
     * For Mirror sync event, the input request should point to mirror.
     * 1. If the webhook capability does not contain mirror sync, this version with webhooks at least supports ref change
     * For Repo ref event,
     * 1. Input request does not point to mirrors
     * 2. Since this version supports webhooks, it will definitely support pull requests and ref changes
     * 3. We subscribe events depending on if we want to only listen for ref changes, only pull request events or both
     * If we subscribe to pull request events, we subscribe to all of these: when pull requests are opened, closed, deleted or merged.
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
                if (!Collections.disjoint(hooks, MIRROR_SYNCHRONIZED_EVENT.getEventIds())) {
                    supportedEvents.add(MIRROR_SYNCHRONIZED_EVENT);
                } else if (!Collections.disjoint(hooks, REPO_REF_CHANGE.getEventIds())) {
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
                supportedEvents.add(PULL_REQUEST_CLOSED_EVENT);
                supportedEvents.add(PULL_REQUEST_OPENED_EVENT);
            }
        }
        if (supportedEvents.isEmpty()) {
            throw new WebhookNotSupportedException("Remote server does not support the required events.");
        }
        return supportedEvents;
    }

    /**
     * Returns the Bitbucket webhook we have just registered/updated.
     * There should only be one webhook for each Jenkins to Bitbucket connection
     * Possible webhook configurations: mirror sync, ref change, pull request events, ref change and pull request events
     * 1. Registering new webhooks: This only happens when there is no mirror sync webhook and we need one, or if there are
     * no webhooks listening for ref changes or pr events. If one exists, then we just update and add events to existing webhook.
     * 2. Updating/deleting existing webhooks:
     *  2.1 If our request has both ref change and pull request events: we must update any webhook with ref change or pull request events
     *      and make sure both types of events are subscribed
     *  2.2 If our request has only pull request events and we have an existing webhook with only pull request:
     *      Don't change events subscribed (keep it as just pull request events)
     *  2.3 If our request has only ref change and we have an existing webhook with only ref change:
     *      Don't change events subscribed (keep it as just ref changes)
     *  2.4 All other situations, subscribe to both ref changes to pull request events
     *      e.g. If our request is only pull request events but our existing webhook is ref changes, we need our updated webhook to listen to both
     * @param request the input request, collection of events
     * @return the correct registered/updated bitbucket webhook
     */
    private BitbucketWebhook process(WebhookRegisterRequest request,
                                     Collection<BitbucketWebhookEvent> events) {
        String callback = constructCallbackUrl(request);
        List<BitbucketWebhook> ownedHooks =
                webhookClient.getWebhooks(getEventIdAsStrings(REPO_REF_CHANGE, MIRROR_SYNCHRONIZED_EVENT,
                        PULL_REQUEST_OPENED_EVENT, PULL_REQUEST_CLOSED_EVENT))
                        .filter(hook -> hook.getName().equals(request.getName()) || hook.getUrl().equals(callback))
                        .collect(toList());

        //Creating lists of different kinds of webhooks
        List<BitbucketWebhook> webhookWithMirrorSync = ownedHooks.stream()
                .filter(hook -> !Collections.disjoint(hook.getEvents(), MIRROR_SYNCHRONIZED_EVENT.getEventIds()))
                .collect(toList());
        List<BitbucketWebhook> webhookWithRepoRefChangeOnly = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().equals(new HashSet<>(REPO_REF_CHANGE.getEventIds())))
                .collect(toList());

        List<BitbucketWebhook> webhookWithPROnly = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().equals(AllPREventIds))
                .collect(toList());

        List<BitbucketWebhook> webhookWithRepoRefChangeOrPR = ownedHooks
                .stream()
                .filter(hook -> hook.getEvents().stream().anyMatch(refAndPREventIds::contains))
                .collect(toList());

        //Determining whether to create a new webhook
        //We will handle mirror syncs separate from all other events.
        // If no webhook with wanted events exist, we register a new one.
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

        //Determining which existing webhooks to update/delete for ref changes and pull request events
        BitbucketWebhook repoResult = null;
        Collection<BitbucketWebhookEvent> supportedEvents = new HashSet<>();
        if (events.contains(REPO_REF_CHANGE) && events.contains(PULL_REQUEST_OPENED_EVENT)) {
            //we need to update any webhook that listens to pr/ref change and update them to listen to both
            repoResult =
                    handleExistingWebhook(request, webhookWithRepoRefChangeOrPR, events);
        } else if (!webhookWithRepoRefChangeOnly.isEmpty() && events.contains(REPO_REF_CHANGE)) {
            //if we have a ref change only webhook and we only want to listen for ref changes, we don't change that
            repoResult = handleExistingWebhook(request, webhookWithRepoRefChangeOnly, events);
        } else if (!webhookWithPROnly.isEmpty() && events.contains(PULL_REQUEST_OPENED_EVENT)) {
            //if we have a pr only webhook and we only want to listen for prs, we don't change that
            repoResult = handleExistingWebhook(request, webhookWithPROnly, events);
        } else {
            //otherwise e.g. if we have a pr only webhook and we want to listen to ref changes as well now, update
            //any webhooks that listen to either and make them listen to both
            supportedEvents.add(REPO_REF_CHANGE);
            supportedEvents.addAll(allPREvents);
            repoResult = handleExistingWebhook(request, webhookWithRepoRefChangeOrPR, supportedEvents);

        }

        if (mirrorSyncResult != null &&
            mirrorSyncResult.getEvents().containsAll(events.stream().flatMap(bitbucketWebhookEvent -> bitbucketWebhookEvent.getEventIds().stream()).collect(Collectors.toSet()))) {
            return mirrorSyncResult;
        } else {
            return repoResult;
        }
    }

    private String[] getEventIdAsStrings(BitbucketWebhookEvent... event) {
        List<String> list = new ArrayList<>();
        for (BitbucketWebhookEvent e : event) {
            list.addAll(e.getEventIds());
        }
        return list.toArray(new String[0]);
    }

    @Nullable
    private BitbucketWebhook handleExistingWebhook(WebhookRegisterRequest request,
                                                   List<BitbucketWebhook> existingWebhooks,
                                                   Collection<BitbucketWebhookEvent> toSubscribe) {
        BitbucketWebhook result = null;
        if (!existingWebhooks.isEmpty()) {
            result = update(existingWebhooks, request, toSubscribe);
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
        LOGGER.info(format("Existing webhook updated - %s with new webhook %s", existing, r));
        return updated;
    }
}
