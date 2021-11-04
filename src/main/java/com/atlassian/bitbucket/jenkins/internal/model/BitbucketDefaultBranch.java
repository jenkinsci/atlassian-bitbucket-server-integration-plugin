package com.atlassian.bitbucket.jenkins.internal.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDefaultBranch extends BitbucketRef {

    private final String latestCommit;
    private final String latestChangeset;
    private final boolean isDefault;

    @JsonCreator
    public BitbucketDefaultBranch(@JsonProperty("id") String id, @JsonProperty("displayId") String displayId,
            @JsonProperty("type") BitbucketRefType type, @JsonProperty("latestCommit") String latestCommit,
            @JsonProperty("latestChangeset") String latestChangeset, @JsonProperty("isDefault") boolean isDefault)  {
        super(id, displayId, type);
        this.latestCommit = latestCommit;
        this.latestChangeset = latestChangeset;
        this.isDefault = isDefault;
    }

    /**
     * @return the latestCommit
     */
    public String getLatestCommit() {
        return latestCommit;
    }

    /**
     * @return the latestChangeset
     */
    public String getLatestChangeset() {
        return latestChangeset;
    }

    /**
     * @return the isDefault
     */
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketDefaultBranch that = (BitbucketDefaultBranch) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getDisplayId(), that.getDisplayId())
                && Objects.equals(getType(), that.getType()) && Objects.equals(latestCommit, that.latestCommit)
                && Objects.equals(latestChangeset, that.latestChangeset) && Objects.equals(isDefault, that.isDefault);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getDisplayId(), getType(), latestCommit, latestChangeset, isDefault);
    }

}
