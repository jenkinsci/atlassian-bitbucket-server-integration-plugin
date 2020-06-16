package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public class BitbucketBuildStatusFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    public BitbucketBuildStatus fromBuild(Run<?, ?> build, BitbucketCICapabilities ciCapabilities) {
        Job<?, ?> job = build.getParent();
        String key = job.getFullName();
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
                .setName(job.getDisplayName())
                .setDescription(state.getDescriptiveText(build.getDisplayName(), build.getDurationString()));

        if (ciCapabilities.supportsRichBuildStatus()) {
            BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);
            ItemGroup parent = job.getParent();

            bbs.setBuildID(build.getId())
                    .setTestResults(getTestResults(build))
                    .setParent(parent instanceof MultiBranchProject ? parent.getFullName() : job.getFullName());

            if (revisionAction != null) {
                bbs.setRef(revisionAction.getRefName());
            }

            if (state != BuildState.INPROGRESS) {
                bbs.setDuration(build.getDuration());
            }
        }
        return bbs.build();
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
