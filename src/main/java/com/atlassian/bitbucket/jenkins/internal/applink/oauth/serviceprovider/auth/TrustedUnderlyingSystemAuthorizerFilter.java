package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Implementation of this filter should simply assume it like {@link javax.servlet.Filter} except all validation are already done
 * This should simply take further action based on the passed in user name.
 */
public interface TrustedUnderlyingSystemAuthorizerFilter {

    /**
     * Authorize the user in the underlying system
     *
     * @param userName    the user to be authorize
     * @param request     the request
     * @param response    the response
     * @param filterChain the filter chain to continue after authorization
     * @throws IOException for other exceptions
     * @throws ServletException for problems while operating on request or response
     * @throws NoSuchUserException if there is no user with the username
     */
    void authorize(String userName, HttpServletRequest request, HttpServletResponse response,
                   FilterChain filterChain) throws IOException, ServletException, NoSuchUserException;
}
