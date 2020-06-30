package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultJenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;
import jenkins.branch.MultiBranchProject;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public final class BitbucketBuildStatusFactoryImpl implements BitbucketBuildStatusFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    private final JenkinsProvider jenkinsProvider;

    public BitbucketBuildStatusFactoryImpl() {
        this(new DefaultJenkinsProvider());
    }

    BitbucketBuildStatusFactoryImpl(JenkinsProvider jenkinsProvider) {
        this.jenkinsProvider = jenkinsProvider;
    }

    @Override
    public BitbucketBuildStatus createLegacyBuildStatus(Run<?, ?> build) {
        return fromBuild(build, false);
    }

    @Override
    public BitbucketBuildStatus createRichBuildStatus(Run<?, ?> build) {
        return fromBuild(build, true);
    }

    private BitbucketBuildStatus fromBuild(Run<?, ?> build, boolean isRich) {
        Job<?, ?> job = build.getParent();
        String key = job.getFullName();
        String url = jenkinsProvider.get().getRootUrl() + build.getUrl();
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

        if (isRich) {
            BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);
            ItemGroup parent = job.getParent();

            bbs.setBuildNumber(build.getId())
                    .setTestResults(getTestResults(build))
                    .setParent(parent instanceof MultiBranchProject ? parent.getFullName() : job.getFullName());

            if (revisionAction != null) {
                bbs.setRef(revisionAction.getBranchAsRefFormat());
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
