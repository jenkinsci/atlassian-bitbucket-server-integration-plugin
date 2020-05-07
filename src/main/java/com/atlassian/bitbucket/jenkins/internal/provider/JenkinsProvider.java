package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import jenkins.model.Jenkins;

@ImplementedBy(DefaultJenkinsProvider.class)
public interface JenkinsProvider {

    /**
     * Returns the result of calling Jenkins.get()
     *
     * @return the Jenkins instance
     */
    Jenkins get();
}
