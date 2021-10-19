package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import javax.annotation.CheckForNull;
import java.util.Arrays;
import java.util.Collection;

public class BitbucketDeploymentFactoryImpl implements BitbucketDeploymentFactory {

    private static final Collection<Result> successfulResults = Arrays.asList(Result.SUCCESS, Result.UNSTABLE);

    private final DisplayURLProvider displayURLProvider;

    public BitbucketDeploymentFactoryImpl() {
        this(DisplayURLProvider.get());
    }

    @VisibleForTesting
    BitbucketDeploymentFactoryImpl(DisplayURLProvider displayURLProvider) {
        this.displayURLProvider = displayURLProvider;
    }

    @Override
    public BitbucketDeployment createDeployment(Run<?, ?> run, BitbucketDeploymentEnvironment environment) {
        return createDeployment(run, environment, null);
    }

    @Override
    public BitbucketDeployment createDeployment(Run<?, ?> run,
                                                BitbucketDeploymentEnvironment environment,
                                                @CheckForNull DeploymentState state) {
        Job<?, ?> job = run.getParent();
        String key = job.getFullName();
        String name = getName(job);
        state = state == null ? getStateFromRun(run) : state;
        String description = state.getDescriptiveText(name, environment.getName());
        String url = displayURLProvider.getRunURL(run);

        return new BitbucketDeployment(run.getNumber(), description, name, environment, key, state, url);
    }

    private String getName(Job<?, ?> job) {
        if (job.getParent() instanceof MultiBranchProject) {
            return job.getParent().getDisplayName() + " » " + job.getDisplayName();
        }
        return job.getDisplayName();
    }

    private DeploymentState getStateFromRun(Run<?, ?> run) {
        Result result = run.getResult();
        if (result == null) {
            return DeploymentState.IN_PROGRESS;
        }
        if (successfulResults.contains(result)) {
            return DeploymentState.SUCCESSFUL;
        }
        return DeploymentState.FAILED;
    }
}
