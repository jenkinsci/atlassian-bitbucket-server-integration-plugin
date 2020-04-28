package com.atlassian.bitbucket.jenkins.internal.scm;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.removeStart;

/**
 * Factory class for {@link BitbucketRefNameExtractor}
 */
public class BitbucketRefNameExtractorFactory {

    /**
     * Returns the proper {@link BitbucketRefNameExtractor} implementation based on the {@link Run job type}
     * <p>
     * The {@link Branch#getName() branch names} inside the {@link Revision} instance passed in to
     * {@link GitSCMExtension} when invoked by {@link GitSCM} can look different for the same branch, based on the build
     * type (e.g. {@link FreeStyleBuild free-style} vs. {@link WorkflowRun multi-branch builds}), which is why we need a
     * different implementation of this interface for different build types.
     *
     * @param buildType the type of the build being run
     * @return the {@link BitbucketRefNameExtractor} implementation for the given build type
     */
    public BitbucketRefNameExtractor forBuildType(@SuppressWarnings("rawtypes") Class<? extends Run> buildType) {
        if (WorkflowRun.class.isAssignableFrom(requireNonNull(buildType, "buildType"))) {
            return new WorkflowRunBitbucketRefNameExtractor();
        }
        return new DefaultBitbucketRefNameExtractor();
    }

    /**
     * The default implementation that expects the {@code branchSpec} to be prepended with the {@code repositoryName}
     */
    private static final class DefaultBitbucketRefNameExtractor implements BitbucketRefNameExtractor {

        @Override
        public String extractRefName(String branchSpec, String repositoryName) {
            return removeStart(branchSpec, requireNonNull(repositoryName, "repositoryName") + "/");
        }
    }

    /**
     * The {@link BitbucketRefNameExtractor} implementation for the {@link WorkflowRun multi-branch build type}
     */
    @VisibleForTesting
    static final class WorkflowRunBitbucketRefNameExtractor implements BitbucketRefNameExtractor {

        @Override
        public String extractRefName(String branchSpec, String repositoryName) {
            return branchSpec;
        }
    }
}
