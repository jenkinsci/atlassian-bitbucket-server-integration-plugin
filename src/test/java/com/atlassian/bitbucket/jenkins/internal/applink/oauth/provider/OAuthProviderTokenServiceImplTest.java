package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.OAuthProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.OAuthToken;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import org.acegisecurity.Authentication;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;
import java.util.Optional;

import static java.lang.System.currentTimeMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OAuthProviderTokenServiceImplTest {

    @InjectMocks
    private OAuthProviderTokenServiceImpl tokenService;
    @Mock
    private OAuthProviderTokenStore tokenStore;
    @Mock
    private OAuthProviderVerifierService verifierService;

    @Test
    public void testAuthorizeRequestToken() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(false, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        String verifier = "some-random-verifier";
        when(verifierService.generateVerifier()).thenReturn(verifier);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        String authorizedBy = "test-user-name";
        when(auth.getName()).thenReturn(authorizedBy);

        OAuthToken authorizedToken = tokenService.authorizeRequestToken(reqTokenValue, auth);

        assertThat(authorizedToken, authorizedRequestToken(callbackUrl, consumerKey, verifier, authorizedBy));
    }

    @Test(expected = NullPointerException.class)
    public void testAuthorizeRequestTokenNullAuthentication() throws OAuthException {
        tokenService.authorizeRequestToken("req-token1", null);
    }

    @Test(expected = NullPointerException.class)
    public void testAuthorizeRequestTokenNullRequestToken() throws OAuthException {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        String authorizedBy = "test-user-name";
        when(auth.getName()).thenReturn(authorizedBy);

        tokenService.authorizeRequestToken(null, auth);
    }

    @Test(expected = OAuthException.class)
    public void testAuthorizeRequestTokenTokenIsAccessToken() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(true, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        String authorizedBy = "test-user-name";
        when(auth.getName()).thenReturn(authorizedBy);

        tokenService.authorizeRequestToken(reqTokenValue, auth);
    }

    @Test(expected = OAuthException.class)
    public void testAuthorizeRequestTokenTokenNotFound() throws OAuthException {
        String reqTokenValue = "req-token1";
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.empty());

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        String authorizedBy = "test-user-name";
        when(auth.getName()).thenReturn(authorizedBy);

        tokenService.authorizeRequestToken(reqTokenValue, auth);
    }

    @Test(expected = OAuthException.class)
    public void testAuthorizeRequestTokenUserIsAnonymous() throws OAuthException {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        String authorizedBy = "anonymous";
        when(auth.getName()).thenReturn(authorizedBy);

        tokenService.authorizeRequestToken("req-token", auth);
    }

    @Test(expected = OAuthException.class)
    public void testAuthorizeRequestTokenUserNotAuthenticated() throws OAuthException {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        tokenService.authorizeRequestToken("req-token", auth);
    }

    @Test
    public void testGenerateAccessToken() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(false, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        requestToken.setAuthorizedBy("some-user");
        requestToken.setVerifier("some-random-verifier");
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        OAuthToken accessToken = tokenService.generateAccessToken(reqTokenValue);

        assertThat(accessToken, accessToken(callbackUrl, consumerKey));
        verify(tokenStore).remove(reqTokenValue);
        verify(tokenStore).put(accessToken);
    }

    @Test(expected = OAuthException.class)
    public void testGenerateAccessTokenInputIsAccessToken() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(true, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        requestToken.setAuthorizedBy("some-user");
        requestToken.setVerifier("some-random-verifier");
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        tokenService.generateAccessToken(reqTokenValue);
    }

    @Test(expected = NullPointerException.class)
    public void testGenerateAccessTokenNullInput() throws OAuthException {
        tokenService.generateAccessToken(null);
    }

    @Test(expected = OAuthException.class)
    public void testGenerateAccessTokenRequestTokenNotAuthorized() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(false, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        requestToken.setVerifier("some-random-verifier");
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        tokenService.generateAccessToken(reqTokenValue);
    }

    @Test(expected = OAuthException.class)
    public void testGenerateAccessTokenRequestTokenNotFound() throws OAuthException {
        String reqTokenValue = "req-token1";
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.empty());

        tokenService.generateAccessToken(reqTokenValue);
    }

    @Test(expected = OAuthException.class)
    public void testGenerateAccessTokenRequestTokenNotVerified() throws OAuthException {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        String reqTokenValue = "req-token1";
        OAuthToken requestToken =
                new OAuthToken(false, callbackUrl, consumerKey, "", currentTimeMillis(), reqTokenValue);
        requestToken.setAuthorizedBy("some-user");
        when(tokenStore.get(reqTokenValue)).thenReturn(Optional.of(requestToken));

        tokenService.generateAccessToken(reqTokenValue);
    }

    @Test
    public void testGenerateRequestToken() {
        String callbackUrl = "http://some-callback-url/endpoint";
        String consumerKey = "test-consumer";
        OAuthConsumer consumer = new OAuthConsumer(callbackUrl, consumerKey, "", null);

        OAuthToken requestToken = tokenService.generateRequestToken(consumer, callbackUrl, null);

        assertThat(requestToken, unAuthorizedRequestToken(callbackUrl, consumerKey));
        verify(tokenStore).put(requestToken);
    }

    @Test(expected = NullPointerException.class)
    public void testGenerateRequestTokenConsumerIsNull() {
        tokenService.generateRequestToken(null, "http://some-callback-url/endpoint", null);

        verify(tokenStore, never()).put(any());
    }

    private static AccessTokenMatcher accessToken(String callbackUrl, String consumerKey) {
        return new AccessTokenMatcher(callbackUrl, consumerKey);
    }

    private static AuthorizedRequestTokenMatcher authorizedRequestToken(String callbackUrl, String consumerKey,
                                                                        String verifier, String authorizedBy) {
        return new AuthorizedRequestTokenMatcher(callbackUrl, consumerKey, verifier, authorizedBy);
    }

    private static UnAuthorizedRequestTokenMatcher unAuthorizedRequestToken(String callbackUrl, String consumerKey) {
        return new UnAuthorizedRequestTokenMatcher(callbackUrl, consumerKey);
    }

    private static final class AccessTokenMatcher extends TypeSafeDiagnosingMatcher<OAuthToken> {

        private final String callbackUrl;
        private final String consumerKey;

        private AccessTokenMatcher(String callbackUrl, String consumerKey) {
            this.callbackUrl = callbackUrl;
            this.consumerKey = consumerKey;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("access token [callbackUrl=")
                    .appendValue(callbackUrl)
                    .appendText(", consumerKey=")
                    .appendValue(consumerKey)
                    .appendText(", accessToken=")
                    .appendValue(true)
                    .appendText(", tokenValue=")
                    .appendValue("not blank")
                    .appendText("]");
        }

        @Override
        protected boolean matchesSafely(OAuthToken accessToken, Description description) {
            if (accessToken == null) {
                description.appendText("is null");
                return false;
            }
            boolean matches = Objects.equals(callbackUrl, accessToken.getCallbackUrl()) &&
                              Objects.equals(consumerKey, accessToken.getConsumerKey()) &&
                              accessToken.isAccessToken() &&
                              StringUtils.isNotBlank(accessToken.getTokenValue());
            if (!matches) {
                description.appendText("request token [callbackUrl=")
                        .appendValue(accessToken.getCallbackUrl())
                        .appendText(", consumerKey=")
                        .appendValue(accessToken.getConsumerKey())
                        .appendText(", accessToken=")
                        .appendValue(accessToken.isAccessToken())
                        .appendText(", tokenValue=")
                        .appendValue(accessToken.getTokenValue())
                        .appendText("]");
            }
            return matches;
        }
    }

    private static final class AuthorizedRequestTokenMatcher extends TypeSafeDiagnosingMatcher<OAuthToken> {

        private final String authorizedBy;
        private final String callbackUrl;
        private final String consumerKey;
        private final String verifier;

        private AuthorizedRequestTokenMatcher(String callbackUrl, String consumerKey, String verifier,
                                              String authorizedBy) {
            this.callbackUrl = callbackUrl;
            this.consumerKey = consumerKey;
            this.verifier = verifier;
            this.authorizedBy = authorizedBy;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("request token [callbackUrl=")
                    .appendValue(callbackUrl)
                    .appendText(", consumerKey=")
                    .appendValue(consumerKey)
                    .appendText(", accessToken=")
                    .appendValue(false)
                    .appendText(", tokenValue=")
                    .appendValue("not blank")
                    .appendText(", verifier=")
                    .appendValue(verifier)
                    .appendText(", authorizedBy=")
                    .appendValue(authorizedBy)
                    .appendText("]");
        }

        @Override
        protected boolean matchesSafely(OAuthToken requestToken, Description description) {
            if (requestToken == null) {
                description.appendText("is null");
                return false;
            }
            boolean matches = Objects.equals(callbackUrl, requestToken.getCallbackUrl()) &&
                              Objects.equals(consumerKey, requestToken.getConsumerKey()) &&
                              !requestToken.isAccessToken() &&
                              StringUtils.isNotBlank(requestToken.getTokenValue());
            if (!matches) {
                description.appendText("request token [callbackUrl=")
                        .appendValue(requestToken.getCallbackUrl())
                        .appendText(", consumerKey=")
                        .appendValue(requestToken.getConsumerKey())
                        .appendText(", accessToken=")
                        .appendValue(requestToken.isAccessToken())
                        .appendText(", tokenValue=")
                        .appendValue(requestToken.getTokenValue())
                        .appendText(", verifier=")
                        .appendValue(requestToken.getVerifier())
                        .appendText(", authorizedBy=")
                        .appendValue(requestToken.getAuthorizedBy())
                        .appendText("]");
            }
            return matches;
        }
    }

    private static final class UnAuthorizedRequestTokenMatcher extends TypeSafeDiagnosingMatcher<OAuthToken> {

        private final String callbackUrl;
        private final String consumerKey;

        private UnAuthorizedRequestTokenMatcher(String callbackUrl, String consumerKey) {
            this.callbackUrl = callbackUrl;
            this.consumerKey = consumerKey;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("request token [callbackUrl=")
                    .appendValue(callbackUrl)
                    .appendText(", consumerKey=")
                    .appendValue(consumerKey)
                    .appendText(", accessToken=")
                    .appendValue(false)
                    .appendText(", tokenValue=")
                    .appendValue("not blank")
                    .appendText("]");
        }

        @Override
        protected boolean matchesSafely(OAuthToken requestToken, Description description) {
            if (requestToken == null) {
                description.appendText("is null");
                return false;
            }
            boolean matches = Objects.equals(callbackUrl, requestToken.getCallbackUrl()) &&
                              Objects.equals(consumerKey, requestToken.getConsumerKey()) &&
                              !requestToken.isAccessToken() &&
                              StringUtils.isNotBlank(requestToken.getTokenValue());
            if (!matches) {
                description.appendText("request token [callbackUrl=")
                        .appendValue(requestToken.getCallbackUrl())
                        .appendText(", consumerKey=")
                        .appendValue(requestToken.getConsumerKey())
                        .appendText(", accessToken=")
                        .appendValue(requestToken.isAccessToken())
                        .appendText(", tokenValue=")
                        .appendValue(requestToken.getTokenValue())
                        .appendText("]");
            }
            return matches;
        }
    }
}
