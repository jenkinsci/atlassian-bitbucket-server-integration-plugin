package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRequestExecutor;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

@Extension
public class UserOAuthTokenTransientActionFactory extends TransientUserActionFactory {

    private static final Logger log = Logger.getLogger(BitbucketRequestExecutor.class.getName());

    @Override
    public Collection<? extends Action> createFor(User target) {
        try {
            return Collections.singleton(ExtensionList.lookupSingleton(OAuthTokenConfiguration.class));
        } catch (IllegalStateException e) {
            log.info("Exception occurred while serving token configuration action: " + e.getMessage());
            return Collections.emptySet();
        }
    }
}
