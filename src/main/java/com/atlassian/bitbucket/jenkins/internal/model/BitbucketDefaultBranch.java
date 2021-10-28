package com.atlassian.bitbucket.jenkins.internal.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDefaultBranch {

    private final String id;
    private final String displayId;
    private final String type;
    private final String latestCommit;
    private final String latestChangeset;
    private final boolean isDefault;

    @JsonCreator
    public BitbucketDefaultBranch(@JsonProperty("id") String id, @JsonProperty("displayId") String displayId,
            @JsonProperty("type") String type, @JsonProperty("latestCommit") String latestCommit,
            @JsonProperty("latestChangeset") String latestChangeset, @JsonProperty("isDefault") boolean isDefault) {
        this.id = id;
        this.displayId = displayId;
        this.type = type;
        this.latestCommit = latestCommit;
        this.latestChangeset = latestChangeset;
        this.isDefault = isDefault;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the displayId
     */
    public String getDisplayId() {
        return displayId;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
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
        return Objects.equals(id, that.id) && Objects.equals(displayId, that.displayId)
                && Objects.equals(type, that.type) && Objects.equals(latestCommit, that.latestCommit)
                && Objects.equals(latestChangeset, that.latestChangeset) && Objects.equals(isDefault, that.isDefault);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayId, type, latestCommit, latestChangeset, isDefault);
    }

}
