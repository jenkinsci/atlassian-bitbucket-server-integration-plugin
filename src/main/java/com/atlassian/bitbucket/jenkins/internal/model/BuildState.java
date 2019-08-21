package com.atlassian.bitbucket.jenkins.internal.model;

import hudson.model.AbstractBuild;
import hudson.model.Result;

import java.util.Arrays;
import java.util.Collection;

/**
 * The state or result of a build
 */
public enum BuildState {

    FAILED("%s failed in %s"),
    INPROGRESS("%s in progress"),
    SUCCESSFUL("%s successful in %s");

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    private final String formatString;

    BuildState(String formatString) {
        this.formatString = formatString;
    }

    public static BuildState fromBuild(AbstractBuild build) {
        if (build.isBuilding()) {
            return INPROGRESS;
        } else if (successfulResults.contains(build.getResult())) {
            return SUCCESSFUL;
        } else {
            return FAILED;
        }
    }

    public String getDescriptiveText(AbstractBuild build) {
        return String.format(formatString, build.getDisplayName(), build.getDurationString());
    }
}
