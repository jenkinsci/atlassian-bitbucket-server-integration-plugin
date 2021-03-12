package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDirectoryChild {

    private String node;
    private BitbucketDirectoryPath path;
    private String type;

    @JsonCreator
    public BitbucketDirectoryChild(
            @JsonProperty(value = "node") String node,
            @JsonProperty(value = "path") BitbucketDirectoryPath path,
            @JsonProperty(value = "type") String type) {
        this.node = node;
        this.path = path;
        this.type = type;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public BitbucketDirectoryPath getPath() {
        return path;
    }

    public void setPath(BitbucketDirectoryPath path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
