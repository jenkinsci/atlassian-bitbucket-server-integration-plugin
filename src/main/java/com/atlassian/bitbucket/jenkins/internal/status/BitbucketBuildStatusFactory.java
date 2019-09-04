package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import java.util.Arrays;
import java.util.Collection;

public class BitbucketBuildStatusFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    public static BitbucketBuildStatus fromBuild(AbstractBuild build) {
        String key = build.getId();
        String url = DisplayURLProvider.get().getRunURL(build);
        BuildState state;
        if (build.isBuilding()) {
            state = BuildState.INPROGRESS;
        } else if (successfulResults.contains(build.getResult())) {
            state = BuildState.SUCCESSFUL;
        } else {
            state = BuildState.FAILED;
        }

        return new BitbucketBuildStatus.Builder(key, state, url)
                .setDescription(state.getDescriptiveText(build.getDisplayName(), build.getDurationString()))
                .setName(build.getProject().getName())
                .build();
    }
}
