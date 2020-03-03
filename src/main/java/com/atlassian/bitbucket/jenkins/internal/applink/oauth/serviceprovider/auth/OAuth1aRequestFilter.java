package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.OAuthConverter;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.server.OAuthServlet;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.OAuthRequestUtils.isOAuthAccessAttempt;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils.logOAuthProblem;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils.logOAuthRequest;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.logging.Level.*;
import static javax.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static net.oauth.OAuth.Problems.*;
import static net.oauth.OAuthMessage.AUTH_SCHEME;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

public class OAuth1aRequestFilter implements Filter {

    private static final Logger log = Logger.getLogger(OAuth1aRequestFilter.class.getName());

    private final ConsumerStore consumerStore;
    private final ServiceProviderTokenStore tokenStore;
    private final OAuthValidator validator;
    private final Clock clock;
    private final TrustedUnderlyingSystemAuthorizerFilter authorizerFilter;

    @Inject
    public OAuth1aRequestFilter(ConsumerStore consumerStore,
                                ServiceProviderTokenStore tokenStore,
                                OAuthValidator validator,
                                Clock clock,
                                TrustedUnderlyingSystemAuthorizerFilter authorizerFilter) {
        this.consumerStore = consumerStore;
        this.tokenStore = tokenStore;
        this.validator = validator;
        this.clock = clock;
        this.authorizerFilter = authorizerFilter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (isOauthRequest(req)) {
            OAuthMessage message = OAuthServlet.getMessage(req, getLogicalUri(req));
            String tokenStr = getTokenFromRequest(req, resp, message);
            if (tokenStr == null) {
                return;
            } else {
                String user;
                try {
                    user = verifyToken(message, tokenStr);
                } catch (OAuthProblemException ope) {
                    handleOAuthProblemException(req, resp, message, ope);
                    return;
                } catch (Exception ex) {
                    handleException(req, resp, message, ex);
                    return;
                }

                try {
                    resp = new OAuthWWWAuthenticateAddingResponse(resp, getBaseUrl(req));
                    authorizerFilter.authorize(user, req, resp, chain);
                    logOAuthRequest(req, "OAuth authentication successful. Request marked as OAuth.", log);
                    return;
                } catch (NoSuchUserException exception) {
                    String msg =
                            format("User %s associated with the token %s not found in the system", user, tokenStr);
                    OAuthServlet.handleException(resp, new OAuthProblemException(msg), getBaseUrl(req));
                }
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    private String verifyToken(OAuthMessage message,
                               String tokenStr) throws OAuthException, IOException, URISyntaxException {
        Optional<ServiceProviderToken> mayBeToken;
        ServiceProviderToken token;
        try {
            // the oauth_token must exist and it has to be valid
            mayBeToken = tokenStore.get(tokenStr);
        } catch (InvalidTokenException e) {
            log.log(FINE, format("3-Legged-OAuth Consumer provided token [%s] rejected by ServiceProviderTokenStore", tokenStr), e);
            throw new OAuthProblemException(TOKEN_REJECTED);
        }

        // various validations on the token
        if (!mayBeToken.isPresent()) {
            if (log.isLoggable(FINE)) {
                log.log(FINE, format("3-Legged-OAuth token rejected. Service Provider Token, for Consumer provided token [%s], is null", tokenStr));
            }

            throw new OAuthProblemException(TOKEN_REJECTED);
        }
        token = mayBeToken.get();

        if (!token.isAccessToken()) {
            if (log.isLoggable(FINE)) {
                log.log(FINE, format("3-Legged-OAuth token rejected. Service Provider Token, for Consumer provided token [%s], is NOT an access token.", tokenStr));
            }

            throw new OAuthProblemException(TOKEN_REJECTED);
        }

        if (token.getUser() == null) {
            if (log.isLoggable(FINE)) {
                log.log(FINE, format("3-Legged-OAuth token rejected. Service Provider Token, for Consumer provided token [%s], does not have a corresponding user.", tokenStr));
            }

            throw new OAuthProblemException("No user associated with the token");
        }

        if (!token.getConsumer().getKey().equals(message.getConsumerKey())) {
            if (log.isLoggable(FINE)) {
                log.log(FINE, format("3-Legged-OAuth token rejected. Service Provider Token, for Consumer provided token [%s], consumer key [%s] does not match request consumer key [%s]", tokenStr, token.getConsumer().getKey(), message.getConsumerKey()));
            }

            throw new OAuthProblemException(TOKEN_REJECTED);
        }

        if (token.hasExpired(clock)) {
            if (log.isLoggable(FINE)) {
                log.log(FINE, format("3-Legged-OAuth token rejected. Token has expired. Token creation time [%d] time to live [%d] clock (contains logging delay) [%d]", token.getCreationTime(), token.getTimeToLive(), clock.millis()));
            }

            throw new OAuthProblemException(TOKEN_EXPIRED);
        }
        validate3LOMessage(message, token);
        validateConsumer(message);
        return token.getUser();
    }

    @CheckForNull
    private String getTokenFromRequest(HttpServletRequest request, HttpServletResponse response, OAuthMessage message) {
        // 3LO needs to start with oauth_token
        try {
            return message.getToken();
        } catch (IOException e) {
            // this would be really strange if it happened, but take precautions just in case
            log.log(SEVERE, "3-Legged-OAuth Failed to read token from request", e);
            sendError(request, response, SC_INTERNAL_SERVER_ERROR, message);
            logOAuthRequest(request, "OAuth authentication FAILED - Unreadable token", log);
            return null;
        }
    }

    private boolean isOauthRequest(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION);
        return startsWithIgnoreCase(authorization, AUTH_SCHEME) && isOAuthAccessAttempt(request);
    }

    private void printMessageToDebug(OAuthMessage message) throws IOException {
        if (!log.isLoggable(FINE)) {
            return;
        }

        StringBuilder sb = new StringBuilder("Validating incoming OAuth request:\n");
        sb.append("\turl: ").append(message.URL).append("\n");
        sb.append("\tmethod: ").append(message.method).append("\n");
        for (Map.Entry<String, String> entry : message.getParameters()) {
            sb.append("\t").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        log.log(FINE, sb.toString());
    }

    private void sendError(HttpServletRequest request, HttpServletResponse response, int status, OAuthMessage message) {
        response.setStatus(status);
        try {
            response.addHeader("WWW-Authenticate", message.getAuthorizationHeader(getBaseUrl(request)));
        } catch (IOException e) {
            log.log(SEVERE, "Failure reporting OAuth error to client", e);
        }
    }

    private Consumer validateConsumer(OAuthMessage message) throws IOException, OAuthException {
        // This consumer must exist at the time the token is used.
        String consumerKey = message.getConsumerKey();
        Consumer consumer = consumerStore.get(consumerKey);

        if (consumer == null) {
            log.log(INFO, "Unknown consumer key:'{}' supplied in OAuth request" + consumerKey);
            throw new OAuthProblemException(CONSUMER_KEY_UNKNOWN);
        }

        return consumer;
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

    private String getLogicalUri(HttpServletRequest request) {
        String uriPathBeforeForwarding = (String) request.getAttribute(FORWARD_REQUEST_URI);
        if (uriPathBeforeForwarding == null) {
            return null;
        }
        URI newUri = URI.create(request.getRequestURL().toString());
        try {
            return new URI(newUri.getScheme(), newUri.getAuthority(),
                    uriPathBeforeForwarding,
                    newUri.getQuery(),
                    newUri.getFragment()).toString();
        } catch (URISyntaxException e) {
            log.log(WARNING, "forwarded request had invalid original URI path: " + uriPathBeforeForwarding);
            return null;
        }
    }

    private void validate3LOMessage(OAuthMessage message, ServiceProviderToken token)
            throws OAuthException, IOException, URISyntaxException {
        printMessageToDebug(message);

        validator.validateMessage(message, OAuthConverter.createOAuthAccessor(token));
    }

    private static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme() + "://";
        String serverName = request.getServerName();
        String serverPort = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + serverName + serverPort + contextPath;
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
