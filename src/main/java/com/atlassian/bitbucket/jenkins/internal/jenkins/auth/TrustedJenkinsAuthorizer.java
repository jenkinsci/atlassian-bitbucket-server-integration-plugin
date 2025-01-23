package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.TrustedUnderlyingSystemAuthorizerFilter;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class TrustedJenkinsAuthorizer implements TrustedUnderlyingSystemAuthorizerFilter {

    private static final Logger log = Logger.getLogger(TrustedJenkinsAuthorizer.class.getName());

    @Override
    public void authorize(User u, HttpServletRequest request, HttpServletResponse response,
                          FilterChain filterChain) throws IOException, ServletException, NoSuchUserException {

        try (ACLContext ignored = createACLContext(u)) {
            log.info("Successfully logged in as user " + u.getId());
            filterChain.doFilter(request, response);
        }
    }

    ACLContext createACLContext(User u) {
        return ACL.as(u);
    }
}
