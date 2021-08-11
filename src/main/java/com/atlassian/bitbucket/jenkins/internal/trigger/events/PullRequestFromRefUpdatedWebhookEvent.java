package com.atlassian.bitbucket.jenkins.internal.trigger.events;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestFromRefUpdatedWebhookEvent extends PullRequestWebhookEvent {

    @JsonCreator
    public PullRequestFromRefUpdatedWebhookEvent(
            @JsonProperty(value = "actor") @Nullable BitbucketUser actor,
            @JsonProperty(value = "eventKey", required = true) String eventKey,
            @JsonProperty(value = "date", required = true) Date date,
            @JsonProperty(value = "pullRequest", required = true) BitbucketPullRequest pullRequest) {
        super(actor, eventKey, date, pullRequest);
    }
}

