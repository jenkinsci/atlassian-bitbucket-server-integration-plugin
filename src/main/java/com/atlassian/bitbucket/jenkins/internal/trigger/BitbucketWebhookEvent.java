package com.atlassian.bitbucket.jenkins.internal.trigger;

public enum BitbucketWebhookEvent {

    REPO_REF_CHANGE("repo:refs_changed"),
    MIRROR_SYNCHRONIZED_EVENT("mirror:repo_synchronized"),
    DIAGNOSTICS_PING_EVENT("diagnostics:ping"),
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
