package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.ByteArrayServletOutputStream;
import net.oauth.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER_WITH_2LO;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.USER;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static net.oauth.OAuth.*;
import static net.oauth.OAuthMessage.AUTH_SCHEME;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OAuth1aRequestFilterTest {

    private static final Map<String, String[]> OAUTH_PARAMETERS = new HashMap<>();

    static {
        OAUTH_PARAMETERS.put(OAUTH_CONSUMER_KEY, new String[]{"consumer-key"});
        OAUTH_PARAMETERS.put(OAUTH_TOKEN, new String[]{"1234"});
        OAUTH_PARAMETERS.put(OAUTH_SIGNATURE_METHOD, new String[]{"RSA-SHA1"});
        OAUTH_PARAMETERS.put(OAUTH_SIGNATURE, new String[]{"signature"});
        OAUTH_PARAMETERS.put(OAUTH_TIMESTAMP, new String[]{"1111111111"});
        OAUTH_PARAMETERS.put(OAUTH_NONCE, new String[]{"1"});
    }

    private static final String TOKEN = "1234";
    private static final ServiceProviderToken ACCESS_TOKEN = ServiceProviderToken.newAccessToken(TOKEN)
            .tokenSecret("5678")
            .consumer(RSA_CONSUMER)
            .authorizedBy(USER)
            .build();
    private static final ServiceProviderToken REQUEST_TOKEN = ServiceProviderToken.newRequestToken(TOKEN)
            .tokenSecret("5678")
            .consumer(RSA_CONSUMER)
            .build();
    private static final ServiceProviderToken EXPIRED_ACCESS_TOKEN = ServiceProviderToken.newAccessToken(TOKEN)
            .tokenSecret("5678")
            .consumer(RSA_CONSUMER)
            .authorizedBy(USER)
            .creationTime(System.currentTimeMillis() - ServiceProviderToken.DEFAULT_ACCESS_TOKEN_TTL * 2)
            .build();

    @Mock
    private ConsumerStore serviceProviderConsumerStore;
    @Mock
    private ServiceProviderTokenStore store;
    @Mock
    private OAuthValidator validator;
    @Mock
    private Clock clock;
    @Mock
    private TrustedUnderlyingSystemAuthorizerFilter trustedUnderlyingSystemAuthorizerFilter;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    private OAuth1aRequestFilter filter;
    private Map<String, String[]> rsaConsumerParameterMap;
    private ByteArrayOutputStream responseOutputStream;

    @Before
    public void setup() throws IOException {
        rsaConsumerParameterMap = new HashMap<>();
        rsaConsumerParameterMap.put("oauth_token", new String[]{TOKEN});
        rsaConsumerParameterMap.put("oauth_consumer_key", new String[]{RSA_CONSUMER.getKey()});
        rsaConsumerParameterMap.put("oauth_signature_method", new String[]{"PLAINTEXT"});
        rsaConsumerParameterMap.put("oauth_signature", new String[]{"&"});
        rsaConsumerParameterMap.put("oauth_timestamp", new String[]{Long.toString(System.currentTimeMillis() / 1000L)});
        rsaConsumerParameterMap.put("oauth_nonce", new String[]{"oauth_nonce"});

        when(request.getRequestURL()).thenReturn(new StringBuffer("http://host/service"));
        when(request.getRequestURI()).thenReturn("/service");
        when(request.getMethod()).thenReturn("GET");

        responseOutputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(responseOutputStream));
        when(clock.millis()).thenReturn(System.currentTimeMillis());
        when(serviceProviderConsumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);

        filter =
                new OAuth1aRequestFilter(serviceProviderConsumerStore, store, validator, clock, trustedUnderlyingSystemAuthorizerFilter);
    }

    @Test
    public void assertThatFailureResultWhenTheConsumerNoLongerExists() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));
        when(serviceProviderConsumerStore.get(RSA_CONSUMER.getKey())).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    public void assertThatSuccessIsReturnedForValidAccessTokenWhenUserCanLogIn() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));

        filter.doFilter(request, response, chain);

        verify(trustedUnderlyingSystemAuthorizerFilter).authorize(argThat(u -> u.equals(USER)), argThat(r -> r.equals(request)), isA(HttpServletResponse.class), argThat(c -> c.equals(chain)));
    }

    @Test
    public void assertThatFailureResultForValidAccessTokenButConsumerKeyDoesNotMatch() throws IOException, ServletException {
        Map<String, String[]> paramMap = new HashMap<String, String[]>(rsaConsumerParameterMap);
        paramMap.put("oauth_consumer_key", new String[]{RSA_CONSUMER_WITH_2LO.getKey()});
        setupRequestWithParameters(paramMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));

        filter.doFilter(request, response, chain);

        verify(trustedUnderlyingSystemAuthorizerFilter, never()).authorize(
                anyString(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class));
    }

    @Test
    public void assertThatFailureResultWithInvalidTokenMessageIsReturnedWhenTokenDoesNotExist() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatFailureResultWithTokenRejectedMessageIsReturnedWhenTokenIsNotAnAccessToken() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(REQUEST_TOKEN));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatFailureResultIsReturnedWhenThereIsAnOAuthProblemDuringMessageValidation() throws Exception {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));
        doThrow(new OAuthProblemException(OAuth.Problems.SIGNATURE_INVALID)).when(validator).validateMessage(isA(OAuthMessage.class), isA(OAuthAccessor.class));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatErrorResultIsReturnedWhenThereIsAGeneralExceptionDuringMessageValidation() throws Exception {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));
        OAuthException toBeThrown = new OAuthException("Unknown problem");
        doThrow(toBeThrown).when(validator).validateMessage(isA(OAuthMessage.class), isA(OAuthAccessor.class));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatFailureResultWithPermissionDeniedMessageIsReturnedForUserWithValidTokenThatCannotLogIn() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));
        doThrow(new NoSuchUserException()).when(trustedUnderlyingSystemAuthorizerFilter)
                .authorize(argThat(u -> u.equals(USER)), argThat(r -> r.equals(request)), isA(HttpServletResponse.class), argThat(c -> c.equals(chain)));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_FORBIDDEN);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatFailureResultIsReturnedWhenInvalidTokenExceptionIsThrownWhileFetchingToken() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenThrow(new InvalidTokenException("token is invalid"));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void assertThatFailureResultIsReturnedWhenTokenIsExpired() throws IOException, ServletException {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(EXPIRED_ACCESS_TOKEN));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith(AUTH_SCHEME));
    }

    @Test
    public void verifyThatWhenOAuthParametersAreNotPresentWeLetTheRequestPassThru() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(isA(HttpServletRequest.class), isA(HttpServletResponse.class));
        verify(response, never()).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verifyZeroInteractions(trustedUnderlyingSystemAuthorizerFilter);
    }

    @Test
    public void verifyThatAuthenticationControllerIsNotifiedAndFilterChainContinuesWhenAuthenticationIsSuccessful() throws Exception {
        setupRequestWithParameters(rsaConsumerParameterMap);
        when(store.get(TOKEN)).thenReturn(Optional.of(ACCESS_TOKEN));

        filter.doFilter(request, response, chain);

        verify(trustedUnderlyingSystemAuthorizerFilter).authorize(
                argThat(u -> u.equals(USER)),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class));
    }

    private void setupRequestWithParameters(Map<String, String[]> params) {
        when(request.getParameterMap()).thenReturn(params);
        when(request.getHeader(AUTHORIZATION)).thenReturn(AUTH_SCHEME);
    }
}