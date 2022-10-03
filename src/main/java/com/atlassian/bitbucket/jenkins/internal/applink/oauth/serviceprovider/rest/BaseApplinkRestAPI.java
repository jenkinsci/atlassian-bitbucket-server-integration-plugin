package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink.ApplinkConfigurationEndpoint;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;

import javax.inject.Inject;

@Extension
public class BaseApplinkRestAPI extends InvisibleAction implements UnprotectedRootAction {

    private static final String URL_BASE = "bitbucket";
    @Inject
    private ApplinkConfigurationEndpoint applinkConfigurationEndpoint;
    @Inject
    private TokenEndpoint tokenEndpoint;

    public Action getOauth() {
        return tokenEndpoint;
    }

    @Override
    public String getUrlName() {
        return URL_BASE;
    }
}