package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@Extension
public class BitbucketWebhookEndpoint implements UnprotectedRootAction {

    public static final String BIBUCKET_WEBHOOK_URL = "bitbucket-server-webhook";
    public static final String X_EVENT_KEY = "X-Event-Key";

    private static final String APPLICATION_JSON = "application/json";
    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookEndpoint.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private BitbucketWebhookConsumer webhookConsumer;

    @POST
    @SuppressWarnings("unused")
    public HttpResponse doTrigger(StaplerRequest request, StaplerResponse response) {
        validateContentType(request);

        String eventKey = getEventKey(request);

        switch (BitbucketWebhookEvent.findByEventId(eventKey)) {
            case DIAGNOSTICS_PING:
                return org.kohsuke.stapler.HttpResponses.ok();
            case REPO_REF_CHANGE:
                return processEvent(request, RefsChangedWebhookEvent.class);
            case MIRROR_SYNCHRONIZED:
                return processEvent(request, MirrorSynchronizedWebhookEvent.class);
            case PULL_REQUEST_DECLINED:
                return processEvent(request, PullRequestDeclinedWebhookEvent.class);
            case PULL_REQUEST_DELETED:
                return processEvent(request, PullRequestDeletedWebhookEvent.class);
            case PULL_REQUEST_FROM_REF_UPDATED:
                return processEvent(request, PullRequestFromRefUpdatedWebhookEvent.class);
            case PULL_REQUEST_MERGED:
                return processEvent(request, PullRequestMergedWebhookEvent.class);
            case PULL_REQUEST_OPENED:
                return processEvent(request, PullRequestOpenedWebhookEvent.class);
            default:
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return HttpResponses.errorJSON("Event is not supported: " + eventKey);
        }
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return BIBUCKET_WEBHOOK_URL;
    }

    private String getEventKey(StaplerRequest request) {
        String eventKey = request.getHeader(X_EVENT_KEY);
        if (StringUtils.isEmpty(eventKey)) {
            throw org.kohsuke.stapler.HttpResponses.errorWithoutStack(
                    HttpServletResponse.SC_BAD_REQUEST, X_EVENT_KEY + " header not set");
        }
        return eventKey;
    }

    private <T> T parse(StaplerRequest request, Class<T> type) {
        try {
            T event = objectMapper.readValue(request.getInputStream(), type);
            LOGGER.fine(String.format("Payload: %s", event));
            return event;
        } catch (IOException e) {
            String error = "Failed to parse the body: " + e.getMessage();
            LOGGER.severe(error);
            throw org.kohsuke.stapler.HttpResponses.errorWithoutStack(HttpServletResponse.SC_BAD_REQUEST, error);
        }
    }

    private <T extends AbstractWebhookEvent> HttpResponse processEvent(StaplerRequest request, Class<T> eventType) {
        T event = parse(request, eventType);
        webhookConsumer.process(event);
        return org.kohsuke.stapler.HttpResponses.ok();
    }

    private void validateContentType(StaplerRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && !contentType.startsWith(APPLICATION_JSON)) {
            LOGGER.severe(String.format("Invalid content type %s", contentType));
            throw org.kohsuke.stapler.HttpResponses.errorWithoutStack(
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Invalid content type: '"
                    + contentType
                    + "'. Content type should be '"
                    + APPLICATION_JSON
                    + "'");
        }
    }
}
