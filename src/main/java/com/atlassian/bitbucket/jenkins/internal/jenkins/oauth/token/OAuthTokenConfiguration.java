package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer.OAuthGlobalConfiguration;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class OAuthTokenConfiguration implements Action, Describable<OAuthGlobalConfiguration> {

    public static final String REVOKE_BUTTON_NAME = "Revoke";

    private Clock clock;
    private ServiceProviderTokenStore tokenStore;
    private User user;

    OAuthTokenConfiguration(Clock clock, ServiceProviderTokenStore tokenStore, User target) {
        this.clock = clock;
        this.tokenStore = tokenStore;
        user = target;
    }

    public OAuthTokenConfiguration() {
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

    @CheckForNull
    @Override
    public String getDisplayName() {
        return Messages.bitbucket_oauth_token_revoke_name();
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @SuppressWarnings("unused") // Stapler
    public List<DisplayAccessToken> getTokens() {
        List<DisplayAccessToken> tokenList = new ArrayList<>();
        user.checkPermission(Jenkins.ADMINISTER);

        tokenStore.getAccessTokensForUser(user.getId())
                .forEach(token -> tokenList.add(new DisplayAccessToken(token, clock)));

        return tokenList;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "tokens";
    }
}
