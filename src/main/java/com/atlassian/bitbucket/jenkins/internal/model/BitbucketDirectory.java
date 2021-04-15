package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDirectory {

    private BitbucketPage<BitbucketDirectoryChild> children;
    private BitbucketDirectoryPath path;
    private String revision;

    @JsonCreator
    public BitbucketDirectory(
            @JsonProperty(value = "children") BitbucketPage<BitbucketDirectoryChild> children,
            @JsonProperty(value = "path") BitbucketDirectoryPath path,
            @JsonProperty(value = "revision") String revision) {
        this.children = children;
        this.path = path;
        this.revision = revision;
    }

    public BitbucketPage<BitbucketDirectoryChild> getChildren() {
        return children;
    }

    public void setChildren(
            BitbucketPage<BitbucketDirectoryChild> children) {
        this.children = children;
    }

    public BitbucketDirectoryPath getPath() {
        return path;
    }

    public void setPath(BitbucketDirectoryPath path) {
        this.path = path;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }
}
