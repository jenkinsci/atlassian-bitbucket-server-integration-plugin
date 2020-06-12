package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultJenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;

public class OAuthConsumerApplinkInfoAction extends AbstractDescribableImpl<OAuthConsumerUpdateAction> implements Action, ModelObjectWithContextMenu {

    private JenkinsProvider jenkinsProvider;
    private String consumerKey;
    private ServiceProviderConsumerStore consumerStore;

    public OAuthConsumerApplinkInfoAction(String consumerKey, ServiceProviderConsumerStore consumerStore) {
        this(consumerKey, consumerStore, new DefaultJenkinsProvider());
    }

    @VisibleForTesting
    public OAuthConsumerApplinkInfoAction(String consumerKey, ServiceProviderConsumerStore consumerStore, JenkinsProvider jenkinsProvider) {
        this.jenkinsProvider = jenkinsProvider;
        this.consumerKey = consumerKey;
        this.consumerStore = consumerStore;
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return new ContextMenu().from(this, request, response);
    }

    @SuppressWarnings("unused") //Stapler
    public String getRequestTokenUrl() {
        return jenkinsProvider.get().getRootUrl() + "/bitbucket/oauth/request-token";
    }

    @SuppressWarnings("unused") //Stapler
    public String getAccessTokenUrl() {
        return jenkinsProvider.get().getRootUrl() + "/bitbucket/oauth/access-token";
    }

    @SuppressWarnings("unused") //Stapler
    public String getAuthorizeUrl() {
        return jenkinsProvider.get().getRootUrl() + "/bbs-oauth/authorize";
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "help.png";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Applink Setup Help";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return consumerKey;
    }
}
