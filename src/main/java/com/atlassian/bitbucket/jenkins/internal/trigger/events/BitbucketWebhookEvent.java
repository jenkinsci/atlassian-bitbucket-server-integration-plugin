package com.atlassian.bitbucket.jenkins.internal.trigger.events;

public enum BitbucketWebhookEvent {

    REPO_REF_CHANGE("repo:refs_changed"),
    MIRROR_SYNCHRONIZED("mirror:repo_synchronized"),
    DIAGNOSTICS_PING("diagnostics:ping"),
    PULL_REQUEST_DECLINED("pr:declined"),
    PULL_REQUEST_DELETED("pr:deleted"),
    PULL_REQUEST_FROM_REF_UPDATED("pr:from_ref_updated"),
    PULL_REQUEST_MERGED("pr:merged"),
    PULL_REQUEST_OPENED("pr:opened"),
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
            if (event.eventId.equalsIgnoreCase(eventId)) {
                return event;
            }
        }
        return UNSUPPORTED;
    }
}
