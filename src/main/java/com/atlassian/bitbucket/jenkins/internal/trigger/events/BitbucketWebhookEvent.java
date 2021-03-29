package com.atlassian.bitbucket.jenkins.internal.trigger.events;

public enum BitbucketWebhookEvent {

    REPO_REF_CHANGE("repo:refs_changed"),
    MIRROR_SYNCHRONIZED_EVENT("mirror:repo_synchronized"),
    DIAGNOSTICS_PING_EVENT("diagnostics:ping"),
    PULL_REQUEST_OPENED_EVENT("pr:opened"),
    PULL_REQUEST_MERGED("pr:merged"),
    PULL_REQUEST_DECLINED("pr:declined"),
    PULL_REQUEST_DELETED("pr:deleted"),
    UNSUPPORTED("");

    private final String eventId;

    BitbucketWebhookEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
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