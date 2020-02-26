package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import com.atlassian.bitbucket.jenkins.internal.model.TestResultsSummary;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public final class BitbucketBuildStatusFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    public static BitbucketBuildStatus fromBuild(Run<?, ?> build) {
        Job<?, ?> parent = build.getParent();
        String key = parent.getFullName();
        String url = DisplayURLProvider.get().getRunURL(build);
        BuildState state;
        if (build.isBuilding()) {
            state = BuildState.INPROGRESS;
        } else if (successfulResults.contains(build.getResult())) {
            state = BuildState.SUCCESSFUL;
        } else {
            state = BuildState.FAILED;
        }
        BitbucketBuildStatus.Builder bbs = new BitbucketBuildStatus.Builder(key, state, url)
                .setName(parent.getFullDisplayName())
                .setServer(Jenkins.get().getRootUrl())
                .setResultKey(build.getExternalizableId())
                .setDescription(state.getDescriptiveText(build.getDisplayName(), build.getDurationString()))
                .setTestResults(getTestResults(build));
        if (state != BuildState.INPROGRESS) {
            bbs.setDuration(build.getDuration());
        }
        return bbs.build();
    }

    @Nullable
    private static TestResults getTestResults(Run<?, ?> build) {
        TestResultAction results = build.getAction(TestResultAction.class);
        if (results != null) {
            return new TestResults(new TestResultsSummary(results.getTotalCount() - results.getFailCount() - results.getSkipCount(),
                    results.getFailCount(), results.getSkipCount()));
        }
        return null;
    }
}
