package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketMirroredRepositoryDescriptor {

    private final Map<String, List<BitbucketNamedLink>> links;
    private final BitbucketMirror mirrorServer;

    @JsonCreator
    public BitbucketMirroredRepositoryDescriptor(
            @JsonProperty(value = "links", required = true) Map<String, List<BitbucketNamedLink>> links,
            @JsonProperty(value = "mirrorServer", required = true) BitbucketMirror mirrorServer) {
        this.links = requireNonNull(links, "links");
        this.mirrorServer = requireNonNull(mirrorServer, "mirrorServer");
    }

    public BitbucketMirror getMirrorServer() {
        return mirrorServer;
    }

    @Nullable
    public String getSelfLink() {
        List<BitbucketNamedLink> link = links.get("self");
        if (link != null && !link.isEmpty()) {
            return link.get(0).getHref();
        }
        return null;
    }
}
