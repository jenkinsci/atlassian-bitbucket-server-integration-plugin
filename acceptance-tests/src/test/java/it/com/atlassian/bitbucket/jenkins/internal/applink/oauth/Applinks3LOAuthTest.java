package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsApplinksClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsOAuthClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.pageobjects.AuthorizeTokenPage;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.GlobalSecurityConfig;
import org.jenkinsci.test.acceptance.po.JenkinsDatabaseSecurityRealm;
import org.jenkinsci.test.acceptance.po.Login;
import org.jenkinsci.test.acceptance.po.User;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.net.URI;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.jenkinsci.test.acceptance.Matchers.loggedInAs;
import static org.junit.Assert.assertNotNull;

@WithPlugins({"mailer", "matrix-auth", "atlassian-bitbucket-server-integration"})
public class Applinks3LOAuthTest extends AbstractJUnitTest {

    @Inject
    private JenkinsController controller;

    private JenkinsApplinksClient applinksClient;
    private JenkinsOAuthClient oAuthClient;
    private OAuthConsumer oAuthConsumer;
    private User stashUser;

    @Before
    public void setup() throws Exception {
        applinksClient = new JenkinsApplinksClient(getBaseUrl());
        oAuthConsumer = applinksClient.createOAuthConsumer();
        oAuthClient = new JenkinsOAuthClient(getBaseUrl(), oAuthConsumer.getKey(), oAuthConsumer.getSecret());

        // Enable security
        GlobalSecurityConfig sc = new GlobalSecurityConfig(jenkins);
        sc.configure();
        JenkinsDatabaseSecurityRealm realm = sc.useRealm(JenkinsDatabaseSecurityRealm.class);
        realm.allowUsersToSignUp(true);
        sc.save();

        stashUser = realm.signup("bbsuser" + randomNumeric(8));
    }

    @Test
    public void testOAuthDance() throws Exception {
        login(stashUser);

        OAuth1RequestToken requestToken = oAuthClient.getRequestToken();
        assertNotNull(requestToken);
        assertThat(requestToken.getToken(), not(isEmptyOrNullString()));

        String authzUrl = oAuthClient.getAuthorizationUrl(requestToken);
        String oAuthVerifier = new AuthorizeTokenPage(jenkins, URI.create(authzUrl).toURL(), requestToken.getToken())
                .authorize();

        OAuth1AccessToken accessToken = oAuthClient.getAccessToken(requestToken, oAuthVerifier);
        assertNotNull(accessToken);
        assertThat(accessToken.getToken(), not(isEmptyOrNullString()));
    }

    private void login(User user) {
        Login login = jenkins.login().doLogin(user);
        waitFor(login)
                .withTimeout(10, SECONDS)
                .until(loggedInAs(user.fullName()));
    }

    private String getBaseUrl() {
        return controller.getUrl().toString();
    }
}
