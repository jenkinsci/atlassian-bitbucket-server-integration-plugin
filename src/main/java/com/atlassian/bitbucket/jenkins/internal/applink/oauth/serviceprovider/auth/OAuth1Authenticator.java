package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.OAuthConverter;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.OAuthRequestUtils;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import hudson.model.User;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.server.OAuthServlet;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils.logOAuthRequest;
import static java.lang.String.format;
import static java.util.logging.Level.*;
import static javax.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static net.oauth.OAuth.Problems.*;
import static net.oauth.OAuthMessage.AUTH_SCHEME;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

/**
 * Authenticates a request based on provided OAuth1 fields.
 * Tested by the
 */
public class OAuth1Authenticator {

    private static final Logger log = Logger.getLogger(OAuth1Authenticator.class.getName());
    private final Clock clock;
    private final ServiceProviderConsumerStore consumerStore;
    private final OAuthRequestUtils oAuthRequestUtils;
    private final SecurityModeChecker securityChecker;
    private final ServiceProviderTokenStore tokenStore;
    private final OAuthValidator validator;

    @Inject
    public OAuth1Authenticator(ServiceProviderConsumerStore consumerStore,
                               ServiceProviderTokenStore tokenStore,
                               OAuthValidator validator,
                               Clock clock,
                               SecurityModeChecker securityChecker,
                               OAuthRequestUtils oAuthRequestUtils) {
        this.consumerStore = consumerStore;
        this.tokenStore = tokenStore;
        this.validator = validator;
        this.clock = clock;
        this.securityChecker = securityChecker;
        this.oAuthRequestUtils = oAuthRequestUtils;
    }

    /**
     * Authenticate the request.
     *
     * @param req  request to validate
     * @param resp response to write errors etc to in case of failure.
     * @return If the request is successful the authenticated user is returned. If it is not a valid
     * * OAuth1 request null is returned.
     * @throws AuthenticationFailedException Thrown to indicate that Authentication failed and the request should not be
     *                                       allowed to proceed
     */
    @CheckForNull
    public User authenticate(HttpServletRequest req, HttpServletResponse resp) throws AuthenticationFailedException {
        if (!securityChecker.isSecurityEnabled()) {
            return null;
        }

        if (!isOauthRequest(req)) {
            // Not an oauth request. Continue the filter chain
            return null;
        }

        OAuthMessage message = OAuthServlet.getMessage(req, getLogicalUri(req));
        String tokenStr = getTokenFromRequest(req, resp, message);
        if (tokenStr == null) {
            // Can't get the token from the oauth string. Continue the filter chain
            return null;
        }

        // Get the user associated with this token
        String user;
        try {
            user = verifyToken(message, tokenStr);
        } catch (Exception ex) {
            throw new AuthenticationFailedException(null, tokenStr, message, ex);
        }
        try {
            return getUser(user);
        } catch (NoSuchUserException e) {
            throw new AuthenticationFailedException(user, tokenStr, message, e);
        }
    }

    User getUser(String userName) throws NoSuchUserException {
        User user = User.getById(userName, false);
        if (user == null) {
            throw new NoSuchUserException(format("No such user %s in the system", userName));
        }
        return user;
    }

    private static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme() + "://";
        String serverName = request.getServerName();
        String serverPort = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + serverName + serverPort + contextPath;
    }

    @Nullable
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
        return startsWithIgnoreCase(authorization, AUTH_SCHEME) && oAuthRequestUtils.isOAuthAccessAttempt(request);
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

    private void validate3LOMessage(OAuthMessage message, ServiceProviderToken token)
            throws OAuthException, IOException, URISyntaxException {
        printMessageToDebug(message);

        validator.validateMessage(message, OAuthConverter.createOAuthAccessor(token));
    }

    private Consumer validateConsumer(OAuthMessage message) throws IOException, OAuthException {
        // This consumer must exist at the time the token is used.
        String consumerKey = message.getConsumerKey();

        return consumerStore.get(consumerKey).orElseThrow(() -> {
            log.log(INFO, "Unknown consumer key:'{}' supplied in OAuth request" + consumerKey);
            return new OAuthProblemException(CONSUMER_KEY_UNKNOWN);
        });
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
}
