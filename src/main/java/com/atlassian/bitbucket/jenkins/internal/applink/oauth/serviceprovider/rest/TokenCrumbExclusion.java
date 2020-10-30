package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Extension
public class TokenCrumbExclusion extends CrumbExclusion {

    static final String EXCLUSION_PATH = "/bitbucket/oauth/";

    @Override
    public boolean process(HttpServletRequest request, HttpServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
        String pathInfo = request.getPathInfo();
        if (isEmpty(pathInfo) || !pathInfo.startsWith(EXCLUSION_PATH)) {
            return false;
        }
        chain.doFilter(request, response);
        return true;
    }
}
