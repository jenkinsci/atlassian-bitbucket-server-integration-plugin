package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.OAuthRequestUtils;
import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds exception to the CSRF protection filter for OAuth (applink) requests.
 */
@Extension
public class OauthCrumbExclusion extends CrumbExclusion {

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!OAuthRequestUtils.isOAuthAccessAttempt(req) || !OAuthRequestUtils.isOauthTokenRequest(req)) {
            return false;
        }
        chain.doFilter(req, resp);
        return true;
    }
}
