package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;
import okhttp3.HttpUrl;

import java.net.URL;

public class BitbucketBuildStatus {

    private final String description;
    private final String key;
    private final String name;
    private final BuildState state;
    private final URL url;

    public BitbucketBuildStatus(AbstractBuild build) {
        state = BuildState.fromBuild(build);
        description = state.getDescriptiveText(build);
        key = build.getId();
        name = build.getProject().getName();
        //TODO: Something (?) is wrong here
        url = new HttpUrl.Builder()
                .addPathSegment(Jenkins.get().getRootUrl())
                .addPathSegment(build.getUrl())
                .build()
                .url();
    }

    @JsonProperty(value = "description")
    public String getDescription() {
        return description;
    }

    @JsonProperty(value = "key", required = true)
    public String getKey() {
        return key;
    }

    @JsonProperty(value = "name")
    public String getName() {
        return name;
    }

    @JsonProperty(value = "state", required = true)
    public String getState() {
        return state.toString();
    }

    @JsonProperty(value = "url", required = true)
    public String getUrl() {
        return url.toString();
    }
}
