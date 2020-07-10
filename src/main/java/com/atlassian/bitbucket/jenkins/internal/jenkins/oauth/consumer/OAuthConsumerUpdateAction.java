package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthConsumerEntry.OAuthConsumerEntryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultJenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration.RELATIVE_PATH;
import static java.util.Objects.requireNonNull;

public class OAuthConsumerUpdateAction extends AbstractDescribableImpl<OAuthConsumerUpdateAction> implements Action, ModelObjectWithContextMenu {

    private final String consumerKey;
    private final ServiceProviderConsumerStore consumerStore;
    private final JenkinsProvider jenkinsProvider;
    private final ServiceProviderTokenStore tokenStore;

    @VisibleForTesting
    OAuthConsumerUpdateAction(String consumerKey, ServiceProviderConsumerStore consumerStore,
                                     ServiceProviderTokenStore tokenStore, JenkinsProvider jenkinsProvider) {
        this.consumerKey = requireNonNull(consumerKey, "consumerKey");
        this.consumerStore = requireNonNull(consumerStore, "consumerStore");
        this.jenkinsProvider = requireNonNull(jenkinsProvider, "jenkinsProvider");
        this.tokenStore = requireNonNull(tokenStore, "tokenStore");
    }

    public OAuthConsumerUpdateAction(String consumerKey, ServiceProviderConsumerStore consumerStore,
                                     ServiceProviderTokenStore tokenStore) {
        this(consumerKey, consumerStore, tokenStore, new DefaultJenkinsProvider());
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return new ContextMenu().from(this, request, response);
    }

    @RequirePOST
    @SuppressWarnings("unused")
    public HttpResponse doPerformDelete(StaplerRequest req) {
        tokenStore.removeByConsumer(consumerKey);
        consumerStore.delete(consumerKey);
        return HttpResponses.redirectViaContextPath(RELATIVE_PATH);
    }

    @RequirePOST
    @SuppressWarnings("unused")
    public HttpResponse doPerformUpdate(StaplerRequest req) throws ServletException, URISyntaxException {
        Consumer consumer = getConsumerDescriptor().getConsumerFromSubmittedForm(req);
        consumerStore.update(consumer);
        return HttpResponses.redirectViaContextPath(RELATIVE_PATH);
    }

    @Nullable
    @SuppressWarnings("unused") // Stapler
    public OAuthConsumerEntryDescriptor getConsumerDescriptor() {
        OAuthConsumerEntry entry = getConsumerEntry();
        if (entry != null) {
            return entry.getDescriptor();
        }
        return null;
    }

    @CheckForNull
    public OAuthConsumerEntry getConsumerEntry() {
        return consumerStore.get(consumerKey).map(OAuthConsumerEntry::getOAuthConsumerForUpdate).orElse(null);
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    @Override
    public String getDisplayName() {
        return Messages.bitbucket_oauth_consumer_admin_update_description();
    }

    @Override
    public String getIconFileName() {
        return "setting.png";
    }

    @SuppressWarnings("unused") //Stapler
    public String getJenkinsBaseUrl() {
        return jenkinsProvider.get().getRootUrl();
    }

    @SuppressWarnings("unused") //Stapler
    public String getRequestTokenUrl() throws URISyntaxException {
        return new URI(jenkinsProvider.get().getRootUrl()).getPath() + "bitbucket/oauth/request-token";
    }

    @SuppressWarnings("unused") //Stapler
    public String getAccessTokenUrl() throws URISyntaxException {
        return new URI(jenkinsProvider.get().getRootUrl()).getPath() + "bitbucket/oauth/access-token";
    }

    @SuppressWarnings("unused") //Stapler
    public String getAuthorizeUrl() throws URISyntaxException {
        return new URI(jenkinsProvider.get().getRootUrl()).getPath() + "bbs-oauth/authorize";
    }

    @Override
    public String getUrlName() {
        return consumerKey;
    }
}
