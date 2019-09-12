package com.atlassian.bitbucket.jenkins.internal.model;

/**
 * The state or result of a build
 */
public enum BuildState {

    FAILED("%s failed in %s"),
    INPROGRESS("%s in progress"),
    SUCCESSFUL("%s successful in %s");

    private final String formatString;

    BuildState(String formatString) {
        this.formatString = formatString;
    }

    public String getDescriptiveText(String displayName, String durationString) {
        return String.format(formatString, displayName, durationString);
    }
}
