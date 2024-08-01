package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketTag extends BitbucketRef {

    private final String latestCommit;

    @JsonCreator
    public BitbucketTag(@JsonProperty("id") String id,
                        @JsonProperty("displayId") String displayId,
                        @JsonProperty("latestCommit") String latestCommit) {
        super(id, displayId, BitbucketRefType.TAG);
        this.latestCommit = latestCommit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketTag that = (BitbucketTag) o;
        return Objects.equals(getId(), that.getId()) &&
               Objects.equals(getDisplayId(), that.getDisplayId()) &&
               Objects.equals(getType(), that.getType()) &&
               Objects.equals(getLatestCommit(), that.getLatestCommit());
    }

    /**
     * @return the latestCommit
     */
    @CheckForNull
    public String getLatestCommit() {
        return latestCommit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getDisplayId(), getType(), latestCommit);
    }
}
