package com.atlassian.bitbucket.jenkins.internal.trigger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum BitbucketWebhookEvent {

    REPO_REF_CHANGE("repo:refs_changed"),
    MIRROR_SYNCHRONIZED_EVENT("mirror:repo_synchronized"),
    DIAGNOSTICS_PING_EVENT("diagnostics:ping"),
    PULL_REQUEST_OPENED_EVENT("pr:opened"),
    PULL_REQUEST_CLOSED_EVENT("pr:merged", "pr:declined", "pr:deleted"),
    UNSUPPORTED("");

    private final List<String> eventId;

    BitbucketWebhookEvent(String... eventId) {
        this.eventId = Collections.unmodifiableList(Arrays.asList(eventId));
    }

    public List<String> getEventIds() {
        return eventId;
    }

    public static BitbucketWebhookEvent findByEventId(String eventId) {
        for (BitbucketWebhookEvent event : values()) {
            if (event.eventId.contains(eventId)) {
                return event;
            }
        }
        return UNSUPPORTED;
    }
}