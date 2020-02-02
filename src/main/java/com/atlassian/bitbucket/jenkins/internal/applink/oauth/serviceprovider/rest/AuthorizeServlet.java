package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Randomizer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils;
import jenkins.model.Jenkins;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.time.Clock;
import java.util.logging.Logger;

import static jenkins.model.Jenkins.ANONYMOUS;
import static net.oauth.OAuth.OAUTH_TOKEN;
import static net.oauth.OAuth.Problems.*;
import static net.oauth.server.OAuthServlet.handleException;

@Singleton
public class AuthorizeServlet {

    private static final int VERIFIER_LENGTH = 6;
    private static final Logger LOGGER = Logger.getLogger(AuthorizeServlet.class.getName());

    private Clock clock;
    private ServiceProviderTokenStore tokenStore;
    private Randomizer randomizer;

    @Inject
    public AuthorizeServlet(ServiceProviderTokenStore store, Randomizer randomizer, Clock clock) {
        this.tokenStore = store;
        this.randomizer = randomizer;
        this.clock = clock;
    }

    public void authorize(HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException, JSONException {
        if (!request.getMethod().equalsIgnoreCase("GET")) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        ServiceProviderToken token;
        try {
            token = getTokenForAuthorization(request);
        } catch (OAuthProblemException e) {
            OAuthProblemUtils.logOAuthProblem(OAuthServlet.getMessage(request, null), e, LOGGER);
            handleException(response, e, request.getRequestURL().toString(), false);
            return;
        }

        String verifier = randomizer.randomAlphanumericString(AuthorizeServlet.VERIFIER_LENGTH);
        Principal userPrincipal = Jenkins.getAuthentication();
        if (ANONYMOUS.equals(userPrincipal)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            ServiceProviderToken newToken = token.authorize(userPrincipal, verifier);
            tokenStore.put(newToken);
            response.setContentType("application/json");
            OutputStream out = response.getOutputStream();
            JSONObject json = new JSONObject();
            json.put("authorizeCode", newToken.getVerifier());
            out.write(json.toString().getBytes("utf-8"));
        }
    }

    private ServiceProviderToken getTokenForAuthorization(
            HttpServletRequest request) throws OAuthProblemException, IOException {
        OAuthMessage requestMessage = OAuthServlet.getMessage(request, null);
        requestMessage.requireParameters(OAUTH_TOKEN);
        ServiceProviderToken token;
        try {
            token = tokenStore.get(requestMessage.getToken())
                    .orElseThrow(() -> new OAuthProblemException(TOKEN_REJECTED));
        } catch (InvalidTokenException e) {
            throw new OAuthProblemException(TOKEN_REJECTED);
        }
        if (token.isAccessToken()) {
            throw new OAuthProblemException(TOKEN_REJECTED);
        }
        if (token.getAuthorization() == ServiceProviderToken.Authorization.AUTHORIZED ||
            token.getAuthorization() == ServiceProviderToken.Authorization.DENIED) {
            throw new OAuthProblemException(TOKEN_USED);
        }
        if (token.hasExpired(clock)) {
            throw new OAuthProblemException(TOKEN_EXPIRED);
        }
        return token;
    }
}
