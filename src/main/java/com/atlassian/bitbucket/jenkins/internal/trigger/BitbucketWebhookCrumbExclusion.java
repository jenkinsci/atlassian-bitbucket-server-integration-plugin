package com.atlassian.bitbucket.jenkins.internal.trigger;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Adds exception to the CSRF protection filter for {@link BitbucketWebhookEndpoint}.
 */
@Extension
public class BitbucketWebhookCrumbExclusion extends CrumbExclusion {

    private static final String EXCLUSION_PATH = '/' + BIBUCKET_WEBHOOK_URL + '/';

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String pathInfo = req.getPathInfo();
        if (isEmpty(pathInfo) || !pathInfo.startsWith(EXCLUSION_PATH)) {
            return false;
        }
        chain.doFilter(req, resp);
        return true;
    }

}
