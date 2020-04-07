package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthConsumerEntry.OAuthConsumerEntryDescriptor;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.net.URISyntaxException;

import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration.RELATIVE_PATH;
import static java.util.Objects.requireNonNull;

public class OAuthConsumerUpdateAction extends AbstractDescribableImpl<OAuthConsumerUpdateAction> implements Action, ModelObjectWithContextMenu {

    private final String consumerKey;
    private final ServiceProviderConsumerStore consumerStore;
    private final ServiceProviderTokenStore tokenStore;

    public OAuthConsumerUpdateAction(String consumerKey, ServiceProviderConsumerStore consumerStore,
                                     ServiceProviderTokenStore tokenStore) {
        this.consumerKey = requireNonNull(consumerKey, "consumerKey");
        this.consumerStore = requireNonNull(consumerStore, "consumerStore");
        this.tokenStore = tokenStore;
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

    @Override
    public String getUrlName() {
        return consumerKey;
    }
}
