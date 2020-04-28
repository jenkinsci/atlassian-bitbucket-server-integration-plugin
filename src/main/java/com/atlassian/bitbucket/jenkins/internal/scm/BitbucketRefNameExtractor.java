package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Run;

/**
 * Extracts the ref name from a given {@code branchSpec}, which can contain different values based on what type of
 * {@link Run build} is running
 *
 * @see BitbucketRefNameExtractorFactory
 */
public interface BitbucketRefNameExtractor {

    /**
     * Extracts the ref name from the given {@code branchSpec}
     * <p>
     * Based on the build type, the {@code branchSpec} may be prepended with {@code '<repository-name>/'}, in which case
     * the {@code repositoryName} and the leading '/' are removed from the beginning of the {@code branchSpec} and the
     * branch name is returned. Otherwise, the branchSpec is returned unchanged.
     *
     * @param branchSpec     the {@code branchSpec} to extract the branch name from
     * @param repositoryName the name of the SCM repository (that the {@code branchSpec} may be prepended with)
     * @return the branch name, with the {@code repositoryName} prefix removed if necessary
     */
    String extractRefName(String branchSpec, String repositoryName);
}
