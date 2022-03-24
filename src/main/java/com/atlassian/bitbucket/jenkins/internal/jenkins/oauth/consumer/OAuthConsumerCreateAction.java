package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.annotations.NotUpgradeSensitive;
import com.atlassian.bitbucket.jenkins.internal.annotations.UpgradeHandled;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthConsumerEntry.OAuthConsumerEntryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.net.URISyntaxException;

import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration.RELATIVE_PATH;
import static java.util.Objects.requireNonNull;

@NotUpgradeSensitive
public class OAuthConsumerCreateAction extends AbstractDescribableImpl<OAuthConsumerCreateAction> implements Action {

    private final ServiceProviderConsumerStore store;
    // TODO: fix NotUpgradeSensitive so this annotation is not necessary
    @UpgradeHandled(handledBy = "Not upgrade sensitive", removeAnnotationInVersion = "3.2.1")
    private final JenkinsProvider jenkinsProvider;

    public OAuthConsumerCreateAction(ServiceProviderConsumerStore store, JenkinsProvider jenkinsProvider) {
        this.store = requireNonNull(store, "store");
        this.jenkinsProvider = requireNonNull(jenkinsProvider, "jenkinsProvider");
    }

    @RequirePOST
    @SuppressWarnings("unused") // Stapler
    public HttpResponse doPerformCreate(StaplerRequest req) throws ServletException, URISyntaxException, FormException {
        jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
        Consumer consumer = getConsumerDescriptor().getConsumerFromSubmittedForm(req);
        store.add(consumer);
        return HttpResponses.redirectViaContextPath(RELATIVE_PATH + "/consumer/" + consumer.getKey() + "/applinkinfo");
    }

    @SuppressWarnings("unused") // Stapler
    public OAuthConsumerEntryDescriptor getConsumerDescriptor() {
        return OAuthConsumerEntry.getOAuthConsumerForAdd().getDescriptor();
    }

    @Override
    public String getDisplayName() {
        return Messages.bitbucket_oauth_consumer_admin_create_description();
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
    }
}
