package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;

import javax.inject.Inject;

@Extension
public class BaseApplinkRestAPI extends InvisibleAction implements UnprotectedRootAction {

    private static final String URL_BASE = "bitbucket";

    @Inject
    private TokenEndpoint tokenEndpoint;

    @Override
    public String getUrlName() {
        return URL_BASE;
    }

    public Action getOauth() {
        return tokenEndpoint;
    }
}