package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefChange;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RefsChangedWebhookEvent extends AbstractWebhookEvent {

    private final List<BitbucketRefChange> changes;
    private final BitbucketRepository repository;

    @JsonCreator
    public RefsChangedWebhookEvent(
            @JsonProperty(value = "actor") @Nullable BitbucketUser actor,
            @JsonProperty(value = "eventKey", required = true) String eventKey,
            @JsonProperty(value = "date", required = true) Date date,
            @JsonProperty(value = "changes", required = true) List<BitbucketRefChange> changes,
            @JsonProperty(value = "repository", required = true) BitbucketRepository repository) {
        super(actor, eventKey, date);
        this.changes = requireNonNull(changes, "changes");
        this.repository = requireNonNull(repository, "repository");
    }

    public List<BitbucketRefChange> getChanges() {
        return changes;
    }

    public BitbucketRepository getRepository() {
        return repository;
    }
}
