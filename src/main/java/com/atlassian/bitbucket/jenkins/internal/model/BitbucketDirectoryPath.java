package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketDirectoryPath {

    private List<String> components;
    private String name;
    private String parent;
    private String toString;

    @JsonCreator
    public BitbucketDirectoryPath(
            @JsonProperty(value = "component") List<String> components,
            @JsonProperty(value = "name") String name,
            @JsonProperty(value = "parent") String parent,
            @JsonProperty(value = "toString") String toString) {
        this.components = components;
        this.name = name;
        this.parent = parent;
        this.toString = toString;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getToString() {
        return toString;
    }

    public void setToString(String toString) {
        this.toString = toString;
    }
}
