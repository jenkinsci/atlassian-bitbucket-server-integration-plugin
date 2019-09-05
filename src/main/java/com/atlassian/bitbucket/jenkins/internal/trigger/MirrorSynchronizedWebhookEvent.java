package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MirrorSynchronizedWebhookEvent extends RefsChangedWebhookEvent {

    private final BitbucketMirrorServer mirrorServer;
    private final BitbucketRepositorySynchronizationType syncType;

    @JsonCreator
    public MirrorSynchronizedWebhookEvent(
            @JsonProperty(value = "mirrorServer", required = true) BitbucketMirrorServer mirrorServer,
            @JsonProperty(value = "eventKey", required = true) String eventKey,
            @JsonProperty(value = "date", required = true) Date date,
            @JsonProperty(value = "changes", required = true) List<BitbucketRefChange> changes,
            @JsonProperty(value = "repository", required = true) BitbucketRepository repository,
            @JsonProperty(value = "syncType", required = true) BitbucketRepositorySynchronizationType syncType) {
        super(eventKey, date, changes, repository);
        this.mirrorServer = mirrorServer;
        this.syncType = syncType;
    }
}
