package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import hudson.model.User;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils.logOAuthProblem;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils.logOAuthRequest;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.logging.Level.SEVERE;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * Identifies every incoming request to check if it is an OAuth request. For an OAuth request,
 * it follows the OAuth 1.0a specification of checking request signature and verifying the access
 * token.
 * After successful validation, it delegates to {@link TrustedUnderlyingSystemAuthorizerFilter} to
 * establish user context.
 */
public class OAuth1aRequestFilter implements Filter {

    public static final String OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY = "bbdc_authenticated";

    private static final Logger log = Logger.getLogger(OAuth1aRequestFilter.class.getName());

    private final OAuth1Authenticator authenticator;
    private final TrustedUnderlyingSystemAuthorizerFilter authorizerFilter;

    @Inject
    public OAuth1aRequestFilter(OAuth1Authenticator authenticator,
                                TrustedUnderlyingSystemAuthorizerFilter authorizerFilter) {
        this.authenticator = authenticator;
        this.authorizerFilter = authorizerFilter;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        Object authenticated = req.getAttribute(OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY);
        if (authenticated instanceof Boolean) {
            Boolean authed = (Boolean) authenticated;
            if (authed) {
                //nothing to do, the request has already been authenticated. As the token can only be used once
                //any new attempts to authenticate the request will fail.
                req.removeAttribute(OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY);
                chain.doFilter(request, response);
                return;
            }
        }

        // Generate the oauth response and authorize the user
        try {
            User user = authenticator.authenticate(req, resp);
            if (user != null) {
                OAuthWWWAuthenticateAddingResponse oauthResp = new OAuthWWWAuthenticateAddingResponse(resp, getBaseUrl(req));
                authorizerFilter.authorize(user, req, oauthResp, chain);
                logOAuthRequest(req, "OAuth authentication successful. Request marked as OAuth.", log);
            } else {
                chain.doFilter(request, response);
            }
        } catch (AuthenticationFailedException exception) {
            Throwable cause = exception.getCause();
            if(cause instanceof NoSuchUserException) {
                String msg = format("User %s associated with the token %s not found in the system", exception.getUser(), exception.getTokenString());
                OAuthServlet.handleException(resp, new OAuthProblemException(msg), getBaseUrl(req));
            } else if(cause instanceof OAuthProblemException) {
                handleOAuthProblemException(req, resp, exception.getOAuthMessage(), (OAuthProblemException) cause);
            } else if (cause instanceof Exception) {
                handleException(req, resp, exception.getOAuthMessage(), (Exception) cause);
            }

        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    private static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme() + "://";
        String serverName = request.getServerName();
        String serverPort = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + serverName + serverPort + contextPath;
    }

    private void handleOAuthProblemException(HttpServletRequest request, HttpServletResponse response,
                                             OAuthMessage message,
                                             OAuthProblemException ope) {
        logOAuthProblem(message, ope, log);
        try {
            OAuthServlet.handleException(response, ope, getBaseUrl(request));
        } catch (Exception e) {
            // there was an IOE or ServletException, nothing more we can really do
            log.log(SEVERE, "Failure reporting OAuth error to client", e);
        }
    }

    private void handleException(HttpServletRequest request, HttpServletResponse response, OAuthMessage message,
                                 Exception e) {
        // this isn't likely to happen, it would result from some unknown error with the request that the OAuth.net
        // library couldn't handle appropriately
        log.log(SEVERE, "Failed to process OAuth message", e);
        sendError(request, response, SC_INTERNAL_SERVER_ERROR, message);
    }

    private void sendError(HttpServletRequest request, HttpServletResponse response, int status, OAuthMessage message) {
        response.setStatus(status);
        try {
            response.addHeader("WWW-Authenticate", message.getAuthorizationHeader(getBaseUrl(request)));
        } catch (IOException e) {
            log.log(SEVERE, "Failure reporting OAuth error to client", e);
        }
    }
    /**
     * Wraps a HttpServletResponse and listens for the status to be set to a "401 Not authorized" or a 401 error to
     * be sent so that it can add the WWW-Authenticate headers for OAuth.
     */
    public static final class OAuthWWWAuthenticateAddingResponse extends HttpServletResponseWrapper {

        private final String baseUrl;

        public OAuthWWWAuthenticateAddingResponse(HttpServletResponse response, String baseUrl) {
            super(response);
            this.baseUrl = checkNotNull(baseUrl, "baseUrl");
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (sc == SC_UNAUTHORIZED) {
                addOAuthAuthenticateHeader();
            }
            super.sendError(sc, msg);
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (sc == SC_UNAUTHORIZED) {
                addOAuthAuthenticateHeader();
            }
            super.sendError(sc);
        }

        @Override
        public void setStatus(int sc, String sm) {
            if (sc == SC_UNAUTHORIZED) {
                addOAuthAuthenticateHeader();
            }
            super.setStatus(sc, sm);
        }

        @Override
        public void setStatus(int sc) {
            if (sc == SC_UNAUTHORIZED) {
                addOAuthAuthenticateHeader();
            }
            super.setStatus(sc);
        }

        private void addOAuthAuthenticateHeader() {
            try {
                OAuthMessage message = new OAuthMessage(null, null, null);
                addHeader("WWW-Authenticate", message.getAuthorizationHeader(baseUrl));
            } catch (IOException e) {
                // ignore, this will never happen
                throw new RuntimeException("Somehow the OAuth.net library threw an IOException, even though it's not doing any IO operations", e);
            }
        }
    }
}
