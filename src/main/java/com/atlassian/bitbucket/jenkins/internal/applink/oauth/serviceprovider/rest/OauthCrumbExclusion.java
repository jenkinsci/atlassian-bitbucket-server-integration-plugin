package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.OAuthRequestUtils;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.AuthenticationFailedException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.OAuth1Authenticator;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.SecurityModeChecker;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.csrf.CrumbExclusion;
import jenkins.model.Jenkins;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.OAuth1aRequestFilter.OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY;
import static java.lang.String.format;

/**
 * Adds exception to the CSRF protection filter for OAuth (applink) requests.
 */
@Extension
public class OauthCrumbExclusion extends CrumbExclusion {

    private static final Set<String> allowedParts = Set.of("/bitbucket/oauth/request-token", "/bbs-oauth/authorize/performSubmit", "/bitbucket/oauth/access-token");
    @Inject
    OAuthRequestUtils oAuthRequestUtils;
    @Inject
    private OAuth1Authenticator authenticator;
    @Inject
    private SecurityModeChecker securityChecker;

    public OauthCrumbExclusion() {
        //required by Jenkins
    }

    //used by tests
    OauthCrumbExclusion(OAuth1Authenticator authenticator, SecurityModeChecker securityChecker, OAuthRequestUtils oAuthRequestUtils) {
        this.authenticator = authenticator;
        this.securityChecker = securityChecker;
        this.oAuthRequestUtils = oAuthRequestUtils;
    }

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {

        if (req.getPathInfo() == null) {
            //none of the paths we should provide exemptions for should return null here
            return false;
        }

        if (req.getPathInfo().startsWith("/job") && req.getPathInfo().endsWith("/build")) {
            if (!securityChecker.isSecurityEnabled()) {
                // Security is not enabled, so it can't be an oauth request. Continue the filter chain
                return false;
            }
            if (oAuthRequestUtils.isOAuthAccessAttempt(req) || oAuthRequestUtils.isOauthTokenRequest(req)) {
                //we probably have an OAuth1 request, we only allow starting builds via OAuth1 so see if the request is for starting a build
                try {
                    User user = authenticator.authenticate(req, resp);
                    if (user != null) {
                        try (ACLContext ignored = ACL.as(user)) {
                            req.setAttribute(OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY, true);
                            List<String> buildPaths = getBuilds();
                            if (buildPaths.contains(req.getPathInfo())) {
                                chain.doFilter(req, resp);
                                return true;
                            }
                        }
                    }
                    // Generate the oauth response and authorize the user
                } catch (AuthenticationFailedException exception) {
                    String msg = format("User %s associated with the token %s not found in the system", exception.getUser(), exception.getTokenString());
                    OAuthServlet.handleException(resp, new OAuthProblemException(msg), getBaseUrl(req));
                }
            }
        }

        if (allowedParts.contains(req.getPathInfo())) {
            chain.doFilter(req, resp);
            return true;
        }
        return false;
    }

    List<String> getBuilds() {
        return Jenkins.get().getAllItems(Job.class)
                .stream().map(project -> "/" + project.getUrl() + "build")
                .collect(Collectors.toList());
    }

    private static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme() + "://";
        String serverName = request.getServerName();
        String serverPort = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + serverName + serverPort + contextPath;
    }
}
