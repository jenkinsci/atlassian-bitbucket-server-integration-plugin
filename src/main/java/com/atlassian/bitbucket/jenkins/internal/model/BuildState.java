package com.atlassian.bitbucket.jenkins.internal.model;

import hudson.model.AbstractBuild;
import hudson.model.Result;

import java.util.Arrays;
import java.util.Collection;

/**
 * The state or result of a build
 */
public enum BuildState {
    FAILED {
        @Override
        public String getDescriptiveText(AbstractBuild build) {
            return new StringBuilder()
                    .append(build.getDisplayName())
                    .append(" failed in ")
                    .append(build.getDurationString())
                    .toString();
        }
    },
    INPROGRESS {
        @Override
        public String getDescriptiveText(AbstractBuild build) {
            return new StringBuilder()
                    .append(build.getDisplayName())
                    .append(" in progress")
                    .toString();
        }
    },
    SUCCESSFUL {
        @Override
        public String getDescriptiveText(AbstractBuild build) {
            return new StringBuilder()
                    .append(build.getDisplayName())
                    .append(" successful in ")
                    .append(build.getDurationString())
                    .toString();
        }
    };

    private static Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    public static BuildState fromBuild(AbstractBuild build) {
        if (build.isBuilding()) {
            return INPROGRESS;
        } else if (successfulResults.contains(build.getResult())) {
            return SUCCESSFUL;
        } else {
            return FAILED;
        }
    }

    public abstract String getDescriptiveText(AbstractBuild build);
}
