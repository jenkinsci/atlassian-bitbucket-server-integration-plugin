package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequestRef {

    private final String displayId;
    private final String id;
    private final BitbucketRepository repository;
    private final String latestCommit;

    @JsonCreator
    public BitbucketPullRequestRef(
            @JsonProperty("id") String id,
            @JsonProperty("displayId") String displayId,
            @JsonProperty("repository") BitbucketRepository repository,
            @JsonProperty("latestCommit") String latestCommit) {
        this.id = requireNonNull(id, "id");
        this.displayId = requireNonNull(displayId, "displayId");
        this.repository = requireNonNull(repository, "repository");
        this.latestCommit = requireNonNull(latestCommit, "latestCommit");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketPullRequestRef that = (BitbucketPullRequestRef) o;
        return displayId.equals(that.displayId) &&
               id.equals(that.id) &&
               repository.equals(that.repository) &&
               latestCommit.equals(that.latestCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayId, id, repository, latestCommit);
    }

    public String getDisplayId() {
        return displayId;
    }

    public String getId() {
        return id;
    }

    public BitbucketRepository getRepository() {
        return repository;
    }

    public String getLatestCommit() {
        return latestCommit;
    }
}

