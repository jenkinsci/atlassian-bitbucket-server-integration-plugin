package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.*;
import hudson.tasks.junit.TestResultAction;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public final class BitbucketBuildStatusFactoryImpl implements BitbucketBuildStatusFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);
    private static final Collection<Result> cancelledResults = Arrays.asList(Result.ABORTED, Result.NOT_BUILT);

    private final DisplayURLProvider displayURLProvider;

    public BitbucketBuildStatusFactoryImpl() {
        this(DisplayURLProvider.get());
    }

    @VisibleForTesting
    BitbucketBuildStatusFactoryImpl(DisplayURLProvider displayURLProvider) {
        this.displayURLProvider = displayURLProvider;
    }

    @Override
    public BitbucketBuildStatus.Builder prepareBuildStatus(Run<?, ?> build, BitbucketRevisionAction revisionAction) {
        Job<?, ?> job = build.getParent();
        ItemGroup parent = job.getParent();
        boolean isMultibranch = parent instanceof MultiBranchProject;

        String name = isMultibranch ? parent.getDisplayName() + " » " + job.getDisplayName() : job.getDisplayName();

        String key = job.getFullName();
        String url = displayURLProvider.getRunURL(build);
        BuildState state;
        if (build.isBuilding()) {
            state = BuildState.INPROGRESS;
        } else if (successfulResults.contains(build.getResult())) {
            state = BuildState.SUCCESSFUL;
        } else if (cancelledResults.contains(build.getResult())) {
            state = BuildState.CANCELLED;
        } else {
            state = BuildState.FAILED;
        }
        BitbucketBuildStatus.Builder bbs = new BitbucketBuildStatus.Builder(key, state, url)
                .setName(name)
                .setDescription(state.getDescriptiveText(build.getDisplayName(), build.getDurationString()));

        bbs.setBuildNumber(build.getId())
                .setTestResults(getTestResults(build))
                .setParent(isMultibranch ? parent.getFullName() : job.getFullName());

        if (revisionAction != null) {
            bbs.setRef(revisionAction.getRefName());
        }

        if (state != BuildState.INPROGRESS) {
            bbs.setDuration(build.getDuration());
        }
        return bbs;
    }

    @Nullable
    private static TestResults getTestResults(Run<?, ?> build) {
        TestResultAction results = build.getAction(TestResultAction.class);
        if (results != null) {
            return new TestResults(results.getTotalCount() - results.getFailCount() - results.getSkipCount(),
                    results.getFailCount(), results.getSkipCount());
        }
        return null;
    }
}
