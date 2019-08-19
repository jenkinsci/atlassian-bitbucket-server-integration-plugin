package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhookRequest.BitbucketWebhookRequestBuilder.aRequestFor;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebhook extends BitbucketWebhookRequest {

    private final int id;

    @JsonCreator
    public BitbucketWebhook(@JsonProperty(value = "id") int id,
                            @JsonProperty(value = "name") String name,
                            @JsonProperty(value = "events") Set<String> events,
                            @JsonProperty(value = "url") String url,
                            @JsonProperty(value = "active") boolean isActive) {
        super(aRequestFor(events).name(name).withIsActive(isActive).withCallbackTo(url));
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketWebhook that = (BitbucketWebhook) o;

        return new EqualsBuilder()
                .append(id, that.id)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .toHashCode();
    }
}
