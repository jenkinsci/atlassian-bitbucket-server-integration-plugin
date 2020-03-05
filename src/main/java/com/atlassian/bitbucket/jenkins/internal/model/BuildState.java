package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The state or result of a build
 */

public enum BuildState {

    @JsonProperty("FAILED")
    FAILED("%s failed in %s"),
    @JsonProperty("INPROGRESS")
    INPROGRESS("%s in progress"),
    @JsonProperty("SUCCESSFUL")
    SUCCESSFUL("%s successful in %s");

    private final String formatString;

    BuildState(String formatString) {
        this.formatString = formatString;
    }

    public String getDescriptiveText(String displayName, String durationString) {
        return String.format(formatString, displayName, durationString);
    }
}
