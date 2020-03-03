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

import static java.util.Objects.requireNonNull;

public class TrustedJenkinsAuthorizer implements TrustedUnderlyingSystemAuthorizerFilter {

    private static final Logger log = Logger.getLogger(TrustedJenkinsAuthorizer.class.getName());

    @Override
    public void authorize(String userName, HttpServletRequest request, HttpServletResponse response,
                          FilterChain filterChain) throws IOException, ServletException, NoSuchUserException {
        requireNonNull(userName, "userName");
        User u = getUser(userName);
        if (u != null) {
            try (ACLContext ignored = createACLContext(u)) {
                log.info("Successfully logged in as user " + userName);
                filterChain.doFilter(request, response);
            }
        } else {
            throw new NoSuchUserException();
        }
    }

    User getUser(String userName) {
        return User.getById(userName, false);
    }

    ACLContext createACLContext(User u) {
        return ACL.as(u);
    }
}
