package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.github.scribejava.core.model.*;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsApplinksClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsOAuthClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.pageobjects.AuthorizeTokenPage;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.MatrixRow;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.ProjectBasedMatrixAuthorizationStrategy;
import org.jenkinsci.test.acceptance.plugins.matrix_auth.ProjectMatrixProperty;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.GlobalSecurityConfig;
import org.jenkinsci.test.acceptance.po.JenkinsDatabaseSecurityRealm;
import org.jenkinsci.test.acceptance.po.User;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.jenkinsci.test.acceptance.Matchers.loggedInAs;
import static org.jenkinsci.test.acceptance.plugins.matrix_auth.MatrixRow.*;
import static org.junit.Assert.assertNotNull;

@WithPlugins({"mailer", "matrix-auth", "atlassian-bitbucket-server-integration"})
public class ThreeLeggedOAuthAcceptanceTest extends AbstractJUnitTest {

    @Inject
    private JenkinsController controller;

    private JenkinsOAuthClient oAuthClient;

    private FreeStyleJob job;

    private User admin;
    private User user1;
    private User user2;

    @Before
    public void setup() throws Exception {
        JenkinsApplinksClient applinksClient = new JenkinsApplinksClient(getBaseUrl());
        OAuthConsumer oAuthConsumer = applinksClient.createOAuthConsumer();
        oAuthClient = new JenkinsOAuthClient(getBaseUrl(), oAuthConsumer.getKey(), oAuthConsumer.getSecret());

        // Enable security
        GlobalSecurityConfig securityConfig = new GlobalSecurityConfig(jenkins);
        securityConfig.configure();
        JenkinsDatabaseSecurityRealm realm = securityConfig.useRealm(JenkinsDatabaseSecurityRealm.class);
        realm.allowUsersToSignUp(true);
        securityConfig.save();

        user1 = realm.signup("stash-user" + randomNumeric(8));
        user2 = realm.signup("stash-user" + randomNumeric(8));
        admin = realm.signup("admin-user" + randomNumeric(8));

        securityConfig.configure();
        ProjectBasedMatrixAuthorizationStrategy matrixAuthzConfig =
                securityConfig.useAuthorizationStrategy(ProjectBasedMatrixAuthorizationStrategy.class);
        MatrixRow adminMatrixRow = matrixAuthzConfig.addUser(admin.fullName());
        adminMatrixRow.admin();
        MatrixRow user1MatrixRow = matrixAuthzConfig.addUser(user1.fullName());
        user1MatrixRow.on(OVERALL_READ);
        MatrixRow user2MatrixRow = matrixAuthzConfig.addUser(user2.fullName());
        user2MatrixRow.on(OVERALL_READ);
        securityConfig.save();

        job = jenkins.jobs.create();
        job.save();

        job.configure();
        ProjectMatrixProperty pmp = new ProjectMatrixProperty(job);
        pmp.enable.check();
        MatrixRow user1JobMatrixRow = pmp.addUser(user1.fullName());
        user1JobMatrixRow.on(ITEM_READ);
        MatrixRow user2JobMatrixRow = pmp.addUser(user2.fullName());
        user2JobMatrixRow.on(ITEM_BUILD, ITEM_READ);
        job.save();

        jenkins.logout();
    }

    @Test
    public void testAuthorize() {
        OAuth1AccessToken user1AccessToken = getAccessToken(user1);
        OAuth1AccessToken user2AccessToken = getAccessToken(user2);

        String jobBuildPostUrl = String.format("%s/job/%s/build", removeEnd(getBaseUrl(), "/"), job.name);
        OAuthRequest buildRequest = new OAuthRequest(Verb.POST, jobBuildPostUrl);
        buildRequest.addHeader("Accept", "application/json");

        Response user2BuildResponse = oAuthClient.execute(buildRequest, user2AccessToken);
        assertThat(user2BuildResponse, successful());

        Response user1BuildResponse = oAuthClient.execute(buildRequest, user1AccessToken);
        assertThat(user1BuildResponse, unauthorized());
    }

    private OAuth1AccessToken getAccessToken(User user) {
        login(user);

        OAuth1RequestToken requestToken = oAuthClient.getRequestToken();
        assertNotNull(requestToken);
        assertThat(requestToken.getToken(), not(isEmptyOrNullString()));

        String authzUrl = oAuthClient.getAuthorizationUrl(requestToken);
        String oAuthVerifier;
        try {
            oAuthVerifier = new AuthorizeTokenPage(jenkins, URI.create(authzUrl).toURL(), requestToken.getToken())
                    .authorize();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        jenkins.logout();

        OAuth1AccessToken accessToken = oAuthClient.getAccessToken(requestToken, oAuthVerifier);
        assertNotNull(accessToken);
        assertThat(accessToken.getToken(), not(isEmptyOrNullString()));
        return accessToken;
    }

    /*
     * Logs user in via the Jenkins login page.
     * The combination of the ATH-provided {@link Login} page object and the Jenkins login page can be quite flakey,
     * hence the added wait after login here. Unfortunately, it's not possible to add any more wait before login (i.e.
     * wait for the login page to be completely loaded before providing the credentials and clicking submit), unless we
     * write our own login page object, so due to the very optimistic one second wait hard-coded in the page object,
     * this can still be a bit flakey.
     */
    private void login(User user) {
        waitFor(jenkins.login().doLogin(user))
                .withTimeout(10, SECONDS)
                .until(loggedInAs(user.fullName()));
    }

    private String getBaseUrl() {
        return controller.getUrl().toString();
    }

    private static SuccessfulBuildResponseMatcher successful() {
        return new SuccessfulBuildResponseMatcher();
    }

    private static FailedBuildResponseMatcher unauthorized() {
        return new FailedBuildResponseMatcher(401, "Unauthorized");
    }

    private static abstract class BuildResponseMatcher extends TypeSafeDiagnosingMatcher<Response> {

        @Override
        protected boolean matchesSafely(Response response, Description mismatchDescription) {
            if (!doMatchesSafely(response)) {
                try {
                    mismatchDescription.appendText("Has status code ").appendValue(response.getCode())
                            .appendText(" and message: ").appendValue(response.getMessage())
                            .appendText(" (response body: ").appendValue(response.getBody());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }
            return true;
        }

        protected abstract boolean doMatchesSafely(Response response);
    }

    private static final class FailedBuildResponseMatcher extends BuildResponseMatcher {

        private final int statusCode;
        private final String message;

        private FailedBuildResponseMatcher(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Response with status code ").appendValue(statusCode)
                    .appendText(" and message ").appendValue(message);
        }

        @Override
        protected boolean doMatchesSafely(Response response) {
            return response.getCode() == statusCode &&
                   StringUtils.equalsIgnoreCase(message, response.getMessage());
        }
    }

    private static final class SuccessfulBuildResponseMatcher extends BuildResponseMatcher {

        @Override
        public void describeTo(Description description) {
            description.appendText("Successful response");
        }

        @Override
        protected boolean doMatchesSafely(Response response) {
            return response.isSuccessful();
        }
    }
}
