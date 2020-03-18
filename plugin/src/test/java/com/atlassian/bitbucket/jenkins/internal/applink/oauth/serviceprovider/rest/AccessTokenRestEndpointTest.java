package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.OAuthConverter;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenFactory;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.ByteArrayServletOutputStream;
import net.oauth.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.AccessTokenRestEndpoint.OAUTH_SESSION_HANDLE;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.DEFAULT_ACCESS_TOKEN_TTL;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.DEFAULT_SESSION_TTL;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Session.newSession;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER_WITH_2LO;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.USER;
import static net.oauth.OAuth.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AccessTokenRestEndpointTest {

    private static final String SESSION_HANDLE = "abcd";
    private static final String TOKEN_VALUE = "1234";
    private static final String TOKEN_SECRET = "5678";
    private static final String VERIFIER = "9876";

    private static final ServiceProviderToken UNAUTHORIZED_REQUEST_TOKEN =
            ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret(TOKEN_SECRET).consumer(RSA_CONSUMER).build();
    private static final ServiceProviderToken AUTHORIZED_REQUEST_TOKEN =
            UNAUTHORIZED_REQUEST_TOKEN.authorize(USER, VERIFIER);
    private static final ServiceProviderToken EXPIRED_AUTHORIZED_REQUEST_TOKEN =
            ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret(TOKEN_SECRET).consumer(RSA_CONSUMER).creationTime(
                    System.currentTimeMillis() -
                    ServiceProviderToken.DEFAULT_REQUEST_TOKEN_TTL * 2).authorizedBy(USER).verifier(VERIFIER).build();
    private static final OAuthAccessor AUTHORIZED_REQUEST_ACCESSOR =
            OAuthConverter.createOAuthAccessor(AUTHORIZED_REQUEST_TOKEN);
    private static final ServiceProviderToken ACCESS_TOKEN =
            ServiceProviderToken.newAccessToken(TOKEN_VALUE).tokenSecret(TOKEN_SECRET).consumer(RSA_CONSUMER).authorizedBy(USER).session(newSession(SESSION_HANDLE).build()).build();
    private static final ServiceProviderToken ACCESS_TOKEN_WITH_EXPIRED_SESSION =
            ServiceProviderToken.newAccessToken(TOKEN_VALUE).tokenSecret(TOKEN_SECRET).consumer(RSA_CONSUMER).authorizedBy(USER).session(newSession(SESSION_HANDLE).creationTime(
                    System.currentTimeMillis() - DEFAULT_SESSION_TTL * 3).lastRenewalTime(
                    System.currentTimeMillis() - DEFAULT_SESSION_TTL * 2).build()).build();
    private static final ServiceProviderToken RENEWED_ACCESS_TOKEN =
            ServiceProviderToken.newAccessToken(TOKEN_VALUE + "r1").tokenSecret(
                    TOKEN_SECRET + "r1").consumer(RSA_CONSUMER).authorizedBy(USER).session(newSession(
                    SESSION_HANDLE + "r1").build()).build();

    @Mock
    private ServiceProviderTokenStore tokenStore;
    @Mock
    private ServiceProviderTokenFactory factory;
    @Mock
    private OAuthValidator validator;
    @Mock
    private Clock clock;

    private AccessTokenRestEndpoint endpoint;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    private ByteArrayOutputStream responseStream;

    @Before
    public void setUp() throws Exception {
        responseStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(responseStream));
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/jenkins/access-token"));

        endpoint = new AccessTokenRestEndpoint(validator, factory, tokenStore, clock);
    }

    @Test
    public void verifyThatAuthorizedRequestTokenCanBeSwappedForAccessToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER},
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(AUTHORIZED_REQUEST_TOKEN));
        when(tokenStore.put(ACCESS_TOKEN)).thenReturn(ACCESS_TOKEN);
        when(factory.generateAccessToken(AUTHORIZED_REQUEST_TOKEN)).thenReturn(ACCESS_TOKEN);

        endpoint.handleAccessToken(request, response);

        verify(validator).validateMessage(isA(OAuthMessage.class),
                argThat(accessor -> accessor.consumer.consumerKey.equals(AUTHORIZED_REQUEST_ACCESSOR.consumer.consumerKey)));
        verify(tokenStore).put(ACCESS_TOKEN);
        verify(tokenStore).remove(AUTHORIZED_REQUEST_TOKEN.getToken());
        verify(response).setContentType("text/plain");
        assertThat(responseStream.toString(), is(equalTo(
                "oauth_token=1234&oauth_token_secret=5678&oauth_expires_in=" + (DEFAULT_ACCESS_TOKEN_TTL / 1000) +
                "&oauth_session_handle=abcd&oauth_authorization_expires_in=" + (DEFAULT_SESSION_TTL) / 1000)));
    }

    @Test
    public void verifyThatAuthorizedRequestTokenCannotBeSwappedForAccessTokenIfRequestConsumerKeyDoesNotMatchToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER},
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER_WITH_2LO.getKey()}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(AUTHORIZED_REQUEST_TOKEN));

        endpoint.handleAccessToken(request, response);

        verify(validator, never()).validateMessage(isA(OAuthMessage.class), any(OAuthAccessor.class));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=token_rejected")));
    }

    @Test
    public void verifyThatTokenRejectedResponseIsSentForNonExistentRequestTokens() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=token_rejected")));
    }

    @Test
    public void verifyThatTokenExpiredResponseIsSentForExpiredRequestTokens() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(EXPIRED_AUTHORIZED_REQUEST_TOKEN));
        when(clock.millis()).thenReturn(System.currentTimeMillis());

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=token_expired")));
    }

    @Test
    public void verifyThatPermissionUnknownResponseIsSentIfRequestTokenIsUnauthorized() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(UNAUTHORIZED_REQUEST_TOKEN));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=permission_unknown")));
    }

    @Test
    public void verifyThatMessageValidationFailureIsHandled() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER},
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(AUTHORIZED_REQUEST_TOKEN));
        doThrow(new OAuthProblemException("signature_invalid")).when(validator).validateMessage(isA(OAuthMessage.class),
                argThat(accessor -> AUTHORIZED_REQUEST_ACCESSOR.consumer.consumerKey.equals(accessor.consumer.consumerKey)));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=signature_invalid")));
    }

    @Test
    public void verifyThatTokenRejectedResponseIsSentWhenInvalidTokenExceptionIsThrownByStore() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenThrow(new InvalidTokenException("Invalid token"));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=token_rejected")));
    }

    @Test
    public void verifyThatTokenExpiredResponseIsSentWhenAuthorizedRequestTokenHasExpired() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(EXPIRED_AUTHORIZED_REQUEST_TOKEN));
        when(clock.millis()).thenReturn(System.currentTimeMillis());

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=token_expired")));
    }

    @Test
    public void verifyThatParameterAbsentIsSentWhenOAuthTokenIsNotPresent() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_VERIFIER, new String[]{VERIFIER}
        ));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), allOf(
                containsString("oauth_problem=parameter_absent"),
                containsString("oauth_parameters_absent=oauth_token"))
        );
    }

    @Test
    public void verifyThatParameterAbsentIsSentWhenOAuthVerifierIsNotPresent() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(AUTHORIZED_REQUEST_TOKEN));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), allOf(
                containsString("oauth_problem=parameter_absent"),
                containsString("oauth_parameters_absent=oauth_verifier"))
        );
    }

    @Test
    public void verifyThatTokenRejectedIsSentWhenOAuthVerifierDoesNotMatchTokenVerifier() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_VERIFIER, new String[]{"not the verifier"}
        ));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=token_rejected"));
    }

    @Test
    public void verifyThatAccessTokenWithValidSessionCanBeRenewed() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_SESSION_HANDLE, new String[]{SESSION_HANDLE}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(ACCESS_TOKEN));
        when(tokenStore.put(RENEWED_ACCESS_TOKEN)).thenReturn(RENEWED_ACCESS_TOKEN);
        when(factory.generateAccessToken(ACCESS_TOKEN)).thenReturn(RENEWED_ACCESS_TOKEN);

        endpoint.handleAccessToken(request, response);

        verify(tokenStore).put(RENEWED_ACCESS_TOKEN);
        verify(tokenStore).remove(ACCESS_TOKEN.getToken());
        verify(response).setContentType("text/plain");
        assertThat(responseStream.toString(), is(equalTo(
                "oauth_token=1234r1&oauth_token_secret=5678r1&oauth_expires_in=" + (DEFAULT_ACCESS_TOKEN_TTL / 1000) +
                "&oauth_session_handle=abcdr1&oauth_authorization_expires_in=" + (DEFAULT_SESSION_TTL) / 1000)));
    }

    @Test
    public void verifyThatParameterAbsentIsSentWhenRenewingAccessTokenWithoutSessionHandle() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(ACCESS_TOKEN));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), allOf(
                containsString("oauth_problem=parameter_absent"),
                containsString("oauth_parameters_absent=oauth_session_handle")));
    }

    @Test
    public void verifyThatTokenRejectedWhenRenewingAccessTokenWithSessionHandleMismatch() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_SESSION_HANDLE, new String[]{SESSION_HANDLE + "r1"}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(ACCESS_TOKEN));

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=token_rejected"));
    }

    @Test
    public void verifyThatPermissionDeniedIsSentWhenRenewingAccessTokenForExpiredSession() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE},
                OAUTH_SESSION_HANDLE, new String[]{SESSION_HANDLE}
        ));
        when(tokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(ACCESS_TOKEN_WITH_EXPIRED_SESSION));
        when(clock.millis()).thenReturn(System.currentTimeMillis());

        endpoint.handleAccessToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=permission_denied"));
    }

    private Map<String, String[]> mapOf(String k1, String[] v1, String k2, String[] v2) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }

    private Map<String, String[]> mapOf(String k1, String[] v1, String k2, String[] v2, String k3, String[] v3) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        return result;
    }

    private Map<String, String[]> mapOf(String k1, String[] v1) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        return result;
    }
}