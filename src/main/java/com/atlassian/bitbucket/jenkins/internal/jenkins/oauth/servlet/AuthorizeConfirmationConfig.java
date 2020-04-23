package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Randomizer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.OAuthProblemUtils;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsAuthWrapper;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.server.OAuthServlet;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.Clock;
import java.util.Map;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Authorization;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static jenkins.model.Jenkins.ANONYMOUS;
import static net.oauth.OAuth.*;
import static net.oauth.OAuth.Problems.*;

public class AuthorizeConfirmationConfig extends AbstractDescribableImpl<AuthorizeConfirmationConfig> implements Action {

    //Following fields are used in Jelly file
    public static final String ACCESS_REQUEST = "read and write";
    public static final String ALLOW_KEY = "authorize";
    public static final String DENY_KEY = "cancel";
    public static final String OAUTH_TOKEN_PARAM = "oauth_token";
    public static final String OAUTH_CALLBACK_PARAM = OAUTH_CALLBACK;

    private static final String DENIED_STATUS = "denied";
    private static final Logger LOGGER = Logger.getLogger(AuthorizeConfirmationConfig.class.getName());
    private static final int VERIFIER_LENGTH = 6;

    private AuthorizeConfirmationConfigDescriptor descriptor;
    private String callback;
    private ServiceProviderToken serviceProviderToken;

    private AuthorizeConfirmationConfig(AuthorizeConfirmationConfigDescriptor descriptor, String rawToken,
                                        String callback) throws OAuthProblemException {
        this.descriptor = descriptor;
        serviceProviderToken = getTokenForAuthorization(rawToken);
        this.callback = callback;
    }

    @SuppressWarnings("unused") //Stapler
    public HttpResponse doPerformSubmit(
            StaplerRequest request) throws IOException, ServletException {
        JSONObject data = request.getSubmittedForm();
        Map<String, String[]> params = request.getParameterMap();

        Principal userPrincipal = descriptor.jenkinsAuthWrapper.getAuthentication();
        if (ANONYMOUS.getPrincipal().equals(userPrincipal.getName())) {
            return HttpResponses.error(SC_UNAUTHORIZED, "User not logged in.");
        }

        ServiceProviderToken token;
        try {
            token = getTokenForAuthorization(data.getString(OAUTH_TOKEN_PARAM));
        } catch (OAuthProblemException e) {
            OAuthProblemUtils.logOAuthProblem(OAuthServlet.getMessage(request, null), e, LOGGER);
            return HttpResponses.error(e);
        }

        ServiceProviderToken newToken;
        if (params.containsKey(DENY_KEY)) {
            newToken = token.deny(userPrincipal.getName());
        } else if (params.containsKey(ALLOW_KEY)) {
            String verifier = getDescriptor().randomizer.randomAlphanumericString(VERIFIER_LENGTH);
            newToken = token.authorize(userPrincipal.getName(), verifier);
        } else {
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
        }
        getDescriptor().tokenStore.put(newToken);

        String callBackUrl =
                addParameters((String) data.get(OAUTH_CALLBACK_PARAM),
                        OAUTH_TOKEN, newToken.getToken(),
                        OAUTH_VERIFIER,
                        newToken.getAuthorization() == Authorization.AUTHORIZED ? newToken.getVerifier() :
                                DENIED_STATUS);
        return HttpResponses.redirectTo(callBackUrl);
    }

    public String getAccessRequest() {
        return ACCESS_REQUEST;
    }

    @SuppressWarnings("unused") //Stapler
    public String getAuthenticatedUsername() {
        return descriptor.jenkinsAuthWrapper.getAuthentication().getName();
    }

    public String getCallback() {
        return callback;
    }

    @SuppressWarnings("unused") //Stapler
    public String getConsumerName() {
        return serviceProviderToken.getConsumer().getName();
    }

    @Override
    public AuthorizeConfirmationConfigDescriptor getDescriptor() {
        return descriptor;
    }

    public String getDisplayName() {
        return "Authorize";
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @SuppressWarnings("unused") //Stapler
    public String getIconUrl() {
        return Jenkins.get().getRootUrl() +
               "/plugin/atlassian-bitbucket-server-integration/images/bitbucket-to-jenkins.png";
    }

    @SuppressWarnings("unused") //Stapler
    public String getInstanceName() {
        return "Jenkins";
    }

    public String getToken() {
        return serviceProviderToken.getToken();
    }

    @Override
    public String getUrlName() {
        return "authorize";
    }

    private ServiceProviderToken getTokenForAuthorization(String rawToken) throws OAuthProblemException {
        ServiceProviderToken token;
        try {
            token = getDescriptor().tokenStore.get(rawToken)
                    .orElseThrow(() -> new OAuthProblemException(TOKEN_REJECTED));
        } catch (InvalidTokenException e) {
            throw new OAuthProblemException(TOKEN_REJECTED);
        }
        if (token.isAccessToken()) {
            throw new OAuthProblemException(TOKEN_REJECTED);
        }
        if (token.getAuthorization() == Authorization.AUTHORIZED ||
            token.getAuthorization() == Authorization.DENIED) {
            throw new OAuthProblemException(TOKEN_USED);
        }
        if (token.hasExpired(getDescriptor().clock)) {
            throw new OAuthProblemException(TOKEN_EXPIRED);
        }
        return token;
    }

    @Extension
    public static class AuthorizeConfirmationConfigDescriptor extends Descriptor<AuthorizeConfirmationConfig> {

        @Inject
        private JenkinsAuthWrapper jenkinsAuthWrapper;
        @Inject
        private Clock clock;
        @Inject
        private Randomizer randomizer;
        @Inject
        private ServiceProviderTokenStore tokenStore;

        AuthorizeConfirmationConfigDescriptor(JenkinsAuthWrapper jenkinsAuthWrapper,
                                              ServiceProviderTokenStore tokenStore,
                                              Randomizer randomizer,
                                              Clock clock) {
            this.jenkinsAuthWrapper = jenkinsAuthWrapper;
            this.tokenStore = tokenStore;
            this.randomizer = randomizer;
            this.clock = clock;
        }

        public AuthorizeConfirmationConfigDescriptor() {
        }

        public AuthorizeConfirmationConfig createInstance(@Nullable StaplerRequest req) throws FormException {
            try {
                OAuthMessage requestMessage = OAuthServlet.getMessage(req, null);
                requestMessage.requireParameters(OAUTH_TOKEN);
                return new AuthorizeConfirmationConfig(this, requestMessage.getToken(), requestMessage.getParameter(OAUTH_CALLBACK));
            } catch (OAuthProblemException e) {
                throw new FormException(e, e.getProblem());
            } catch (IOException e) {
                throw new FormException(e, e.getMessage());
            }
        }

        @Override
        public AuthorizeConfirmationConfig newInstance(@Nullable StaplerRequest req,
                                                       @Nonnull JSONObject formData) throws FormException {
            return createInstance(req);
        }

        public boolean isAuthenticated() {
            return jenkinsAuthWrapper.getAuthentication().isAuthenticated();
        }
    }
}
