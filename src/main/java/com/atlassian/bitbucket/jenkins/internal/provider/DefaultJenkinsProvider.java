package com.atlassian.bitbucket.jenkins.internal.provider;

import jenkins.model.Jenkins;

public class DefaultJenkinsProvider implements JenkinsProvider {

    public Jenkins get() {
        return Jenkins.get();
    }
}
