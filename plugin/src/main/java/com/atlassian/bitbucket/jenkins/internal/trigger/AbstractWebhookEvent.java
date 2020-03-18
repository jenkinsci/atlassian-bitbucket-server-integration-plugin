package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class AbstractWebhookEvent {

    private final BitbucketUser actor;
    private final Date date;
    private final String eventKey;

    public AbstractWebhookEvent(@Nullable BitbucketUser actor, String eventKey, Date date) {
        this.actor = actor;
        this.eventKey = requireNonNull(eventKey, "eventKey");
        this.date = requireNonNull(date, "date");
    }

    public Optional<BitbucketUser> getActor() {
        return Optional.ofNullable(actor);
    }

    public Date getDate() {
        return new Date(date.getTime());
    }

    public String getEventKey() {
        return eventKey;
    }
}
