package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsAuthWrapper;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

@Extension
public class OAuthTokenConfiguration implements Action, Describable<OAuthGlobalConfiguration> {

    public static final String REVOKE_BUTTON_NAME = "Revoke";

    @Inject
    private JenkinsAuthWrapper jenkinsAuthWrapper;
    @Inject
    private Clock clock;
    @Inject
    private ServiceProviderTokenStore tokenStore;

    OAuthTokenConfiguration(JenkinsAuthWrapper jenkinsAuthWrapper, Clock clock, ServiceProviderTokenStore tokenStore) {
        this.jenkinsAuthWrapper = jenkinsAuthWrapper;
        this.clock = clock;
        this.tokenStore = tokenStore;
    }

    public OAuthTokenConfiguration() {
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return Messages.bitbucket_oauth_token_revoke_name();
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "tokens";
    }

    @SuppressWarnings("unused") // Stapler
    public List<DisplayAccessToken> getTokens() {
        List<DisplayAccessToken> tokenList = new ArrayList<>();
        tokenStore.getAccessTokensForUser(jenkinsAuthWrapper.getAuthentication().getName())
                .forEach(token -> tokenList.add(new DisplayAccessToken(token, clock)));

        return tokenList;
    }

    @RequirePOST
    public HttpResponse doRevoke(StaplerRequest request) {
        request.getParameterMap()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().length == 1 && e.getValue()[0].equals(REVOKE_BUTTON_NAME))
                .map(Entry::getKey)
                .forEach(t -> tokenStore.remove(t));
        return HttpResponses.redirectToDot();
    }

    @Override
    public Descriptor<OAuthGlobalConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }
}
