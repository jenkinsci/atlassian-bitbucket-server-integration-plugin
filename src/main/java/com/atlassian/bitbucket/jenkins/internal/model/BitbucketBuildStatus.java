package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import hudson.model.AbstractBuild;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

public class BitbucketBuildStatus {

    private final String description;
    private final String key;
    private final String name;
    private final BuildState state;
    private final String url;

    public BitbucketBuildStatus(AbstractBuild build) {
        state = BuildState.fromBuild(build);
        description = state.getDescriptiveText(build);
        key = build.getId();
        name = build.getProject().getName();
        url = DisplayURLProvider.get().getRunURL(build);
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
        return url;
    }
}
