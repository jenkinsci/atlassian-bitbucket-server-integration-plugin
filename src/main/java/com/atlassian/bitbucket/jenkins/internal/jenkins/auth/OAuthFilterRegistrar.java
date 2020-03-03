package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.OAuth1aRequestFilter;
import hudson.Extension;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.util.PluginServletFilter;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

@Extension
public class OAuthFilterRegistrar {

    private static final Logger log = Logger.getLogger(OAuthFilterRegistrar.class.getName());

    @Inject
    private OAuth1aRequestFilter requestHandler;

    @Initializer(after = PLUGINS_PREPARED)
    public void onStart() {
        try {
            addFilter();
        } catch (ServletException e) {
            log.log(SEVERE, "Failed to add filter in filter chain", e);
        }
    }

    @Terminator
    public void onStop() {
        try {
            removeFilter();
        } catch (ServletException e) {
            log.log(WARNING, "Failed to remove filter from filter chain", e);
        }
    }

    void addFilter() throws ServletException {
        PluginServletFilter.addFilter(requestHandler);
    }

    void removeFilter() throws ServletException {
        PluginServletFilter.removeFilter(requestHandler);
    }
}
