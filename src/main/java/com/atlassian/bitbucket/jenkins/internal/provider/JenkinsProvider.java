package com.atlassian.bitbucket.jenkins.internal.provider;

import jenkins.model.Jenkins;

import javax.annotation.Nonnull;

public interface JenkinsProvider {

    /**
     * Returns the result of calling Jenkins.get()
     *
     * @return the Jenkins instance
     */
    @Nonnull
    Jenkins get();
}
