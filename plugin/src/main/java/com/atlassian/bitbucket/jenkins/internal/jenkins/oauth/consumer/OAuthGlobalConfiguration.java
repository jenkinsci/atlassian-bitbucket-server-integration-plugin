package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet.AuthorizeAction.AuthorizeActionDescriptor;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token.OAuthTokenConfiguration;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ManagementLink;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.util.Collection;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

@Extension
public class OAuthGlobalConfiguration extends ManagementLink implements Describable<OAuthGlobalConfiguration> {

    public static final String RELATIVE_PATH = "bbs-oauth";

    @Inject
    private ServiceProviderConsumerStore consumerStore;
    @Inject
    private ServiceProviderTokenStore serviceProviderTokenStore;
    @Inject
    private OAuthTokenConfiguration tokenConfiguration;
    @Inject
    private AuthorizeActionDescriptor authorizeActionDescriptor;

    public Collection<OAuthConsumerEntry> getConsumers() {
        return stream(consumerStore.getAll().spliterator(), false).map(OAuthConsumerEntry::getOAuthConsumerForUpdate).collect(toList());
    }

    /**
     * Creates a new update action with an empty consumer that can be created and added
     *
     * @return a create action for a new consumer
     */
    @SuppressWarnings("unused") // Stapler
    public OAuthConsumerCreateAction getCreate() {
        return new OAuthConsumerCreateAction(consumerStore);
    }

    /**
     * Creates a new update action with the existing consumer matching a given key, that can be updated
     *
     * @param key the key of the existing consumer
     * @return an update action for an existing consumer
     */
    @SuppressWarnings("unused") // Stapler
    public OAuthConsumerUpdateAction getConsumer(String key) {
        return new OAuthConsumerUpdateAction(key, consumerStore, serviceProviderTokenStore);
    }

    public Action getTokens() {
        return tokenConfiguration;
    }

    @SuppressWarnings("unused") // Stapler
    public Action getAuthorize(StaplerRequest req) throws FormException {
        return authorizeActionDescriptor.createInstance(req);
    }

    @Override
    public Descriptor<OAuthGlobalConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "BBS OAuth";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return RELATIVE_PATH;
    }

    @Override
    public String getDescription() {
        return "Manage Oauth credentials used in BBS";
    }
}
