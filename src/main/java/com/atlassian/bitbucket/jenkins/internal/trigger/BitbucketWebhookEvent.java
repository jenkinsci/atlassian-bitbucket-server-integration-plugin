package com.atlassian.bitbucket.jenkins.internal.trigger;

public enum BitbucketWebhookEvent {

    REPO_REF_CHANGE("repo:refs_changed"),
    MIRROR_SYNC("mirror:repo_synchronized");

    private final String eventId;

    BitbucketWebhookEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

}
