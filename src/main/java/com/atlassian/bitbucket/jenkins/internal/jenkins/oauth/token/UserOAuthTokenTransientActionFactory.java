package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRequestExecutor;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

@Extension
public class UserOAuthTokenTransientActionFactory extends TransientUserActionFactory {

    private static final Logger log = Logger.getLogger(BitbucketRequestExecutor.class.getName());
    @Inject
    private Clock clock;
    @Inject
    private ServiceProviderTokenStore tokenStore;

    @Override
    public Collection<? extends Action> createFor(User target) {
        if (!target.hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptySet();
        }

        try {
            return Collections.singleton(new OAuthTokenConfiguration(clock, tokenStore, target));
        } catch (IllegalStateException e) {
            log.info("Exception occurred while serving token configuration action: " + e.getMessage());
            return Collections.emptySet();
        }
    }
}
