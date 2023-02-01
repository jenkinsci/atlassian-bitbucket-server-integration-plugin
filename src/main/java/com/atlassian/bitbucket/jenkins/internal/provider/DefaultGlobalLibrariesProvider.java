package com.atlassian.bitbucket.jenkins.internal.provider;

import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import javax.inject.Singleton;

@Singleton
public class DefaultGlobalLibrariesProvider implements GlobalLibrariesProvider {

    public GlobalLibraries get() {
        return GlobalLibraries.get();
    }
}
