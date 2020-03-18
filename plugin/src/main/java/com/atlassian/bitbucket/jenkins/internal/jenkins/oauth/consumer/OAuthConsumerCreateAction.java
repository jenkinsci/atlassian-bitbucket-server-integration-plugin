package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthConsumerEntry.OAuthConsumerEntryDescriptor;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.net.URISyntaxException;

import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration.RELATIVE_PATH;
import static java.util.Objects.requireNonNull;

public class OAuthConsumerCreateAction extends AbstractDescribableImpl<OAuthConsumerCreateAction> implements Action {

    private final ServiceProviderConsumerStore store;

    public OAuthConsumerCreateAction(ServiceProviderConsumerStore store) {
        this.store = requireNonNull(store, "store");
    }

    @RequirePOST
    @SuppressWarnings("unused") // Stapler
    public HttpResponse doPerformCreate(StaplerRequest req) throws ServletException, URISyntaxException {
        Consumer consumer = getConsumerDescriptor().getConsumerFromSubmittedForm(req);
        store.add(consumer);
        return HttpResponses.redirectViaContextPath(RELATIVE_PATH);
    }

    @SuppressWarnings("unused") // Stapler
    public OAuthConsumerEntryDescriptor getConsumerDescriptor() {
        return OAuthConsumerEntry.getOAuthConsumerForAdd().getDescriptor();
    }

    @Override
    public String getDisplayName() {
        return "Create New Consumer";
    }

    @Override
    public String getIconFileName() {
        return "setting.png";
    }

    @Override
    public String getUrlName() {
        return "new";
    }

    @Extension
    @SuppressWarnings("unused") // Stapler
    @Symbol("oauth-consumer-create")
    public static class DescriptorImpl extends Descriptor<OAuthConsumerCreateAction> {

        @Inject
        private ServiceProviderConsumerStore consumerStore;

        @Override
        public String getDisplayName() {
            return "Update OAuth Consumer";
        }
    }
}
