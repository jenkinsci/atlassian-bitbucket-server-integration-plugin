package com.atlassian.bitbucket.jenkins.internal.provider;

import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import javax.inject.Singleton;

@Singleton
public class DefaultJenkinsAuthWrapper implements JenkinsAuthWrapper {

    public Authentication getAuthentication() {
        return Jenkins.getAuthentication();
    }
}
