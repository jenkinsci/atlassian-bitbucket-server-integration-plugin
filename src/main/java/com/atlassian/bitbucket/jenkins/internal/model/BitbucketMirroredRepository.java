package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketMirroredRepository {

    private final boolean available;
    private final Map<String, List<BitbucketNamedLink>> links;
    private final String mirrorName;
    private final int repositoryId;
    private final BitbucketMirroredRepositoryStatus status;

    @JsonCreator
    public BitbucketMirroredRepository(
            @JsonProperty(value = "available", required = true) boolean available,
            @JsonProperty(value = "links", required = true) Map<String, List<BitbucketNamedLink>> links,
            @JsonProperty(value = "mirrorName", required = true) String mirrorName,
            @JsonProperty(value = "repositoryId", required = true) int repositoryId,
            @JsonProperty(value = "status", required = true) BitbucketMirroredRepositoryStatus status) {
        this.available = available;
        this.links = requireNonNull(links, "links");
        this.mirrorName = requireNonNull(mirrorName, "mirrorName");
        this.repositoryId = repositoryId;
        this.status = requireNonNull(status, "status");
    }

    public boolean isAvailable() {
        return available;
    }

    public Map<String, List<BitbucketNamedLink>> getLinks() {
        return links;
    }

    public String getMirrorName() {
        return mirrorName;
    }

    public int getRepositoryId() {
        return repositoryId;
    }

    public BitbucketMirroredRepositoryStatus getStatus() {
        return status;
    }
}
