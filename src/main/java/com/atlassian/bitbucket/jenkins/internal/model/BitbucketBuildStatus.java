package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import hudson.model.Build;
import hudson.model.Result;
import jenkins.model.Jenkins;
import okhttp3.HttpUrl;

public class BitbucketBuildStatus {

    private final String description;
    private final String key;
    private final String name;
    private final String state;
    private final String url;

    public BitbucketBuildStatus(Build build) {
        if (build.isBuilding()) {
            //TODO: Have a method that is equivalent to how Bamboo creates status descriptions
            description = build.getDisplayName() + " in progress";
            state = BuildState.INPROGRESS.toString();
        } else if (build.getResult().equals(Result.SUCCESS)) {
            description = build.getDisplayName() + " successful in X seconds";
            state = BuildState.SUCCESSFUL.toString();
        } else {
            description = build.getDisplayName() + " failed in X seconds";
            state = BuildState.FAILED.toString();
        }
        key = build.getId();
        name = build.getProject().getName();
        url = new HttpUrl.Builder()
                .addPathSegment(Jenkins.get().getRootUrl())
                .addPathSegment(build.getUrl())
                .build()
                .url().toString();
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
        return state;
    }

    @JsonProperty(value = "url", required = true)
    public String getUrl() {
        return url;
    }
}
