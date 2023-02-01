package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.ImplementedBy;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;

@ImplementedBy(DefaultGlobalLibrariesProvider.class)
public interface GlobalLibrariesProvider {

    /**
     * Returns the result of calling GlobalLibraries.get()
     *
     * @return the GlobalLibraries instance
     */
    GlobalLibraries get();
}
