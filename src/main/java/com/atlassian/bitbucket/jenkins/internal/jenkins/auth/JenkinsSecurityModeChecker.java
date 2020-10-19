package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.SecurityModeChecker;
import hudson.security.SecurityMode;
import jenkins.model.Jenkins;

public class JenkinsSecurityModeChecker implements SecurityModeChecker {

    @Override
    public boolean isSecurityEnabled() {
        return !(Jenkins.get().getSecurity() == SecurityMode.UNSECURED);
    }
}
