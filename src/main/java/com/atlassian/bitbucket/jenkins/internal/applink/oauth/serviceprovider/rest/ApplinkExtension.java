package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink.ApplinkConfigurationEndpoint;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink.UrlMapper;
import hudson.model.InvisibleAction;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApplinkExtension extends InvisibleAction {

    private ApplinkConfigurationEndpoint manifestEndpoint;
    private UrlMapper urlMapper;

    @Inject
    public ApplinkExtension(UrlMapper urlMapper, ApplinkConfigurationEndpoint manifestEndpoint) {
        this.urlMapper = urlMapper;
        this.manifestEndpoint = manifestEndpoint;
    }

    public ApplinkExtension() {
        int a = 0;
        System.out.println("ohohoh!");
    }
}
