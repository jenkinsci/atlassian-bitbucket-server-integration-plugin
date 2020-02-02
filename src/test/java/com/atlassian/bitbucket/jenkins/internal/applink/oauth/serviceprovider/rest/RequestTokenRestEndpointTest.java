package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenFactory;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
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
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.RequestTokenRestEndpoint.INVALID_CALLBACK_ADVICE;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;
import static net.oauth.OAuth.OAUTH_CALLBACK;
import static net.oauth.OAuth.OAUTH_CONSUMER_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RequestTokenRestEndpointTest {

    private static final ServiceProviderToken UNAUTHORIZED_REQUEST_TOKEN =
            ServiceProviderToken.newRequestToken("1234").tokenSecret("5678").consumer(RSA_CONSUMER).build();

    @Mock
    private ConsumerStore consumerStore;
    @Mock
    private ServiceProviderTokenStore tokenStore;
    @Mock
    private ServiceProviderTokenFactory factory;
    @Mock
    private OAuthValidator validator;
    private RequestTokenRestEndpoint servlet;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private ByteArrayOutputStream responseStream;

    @Before
    public void setUp() throws Exception {
        responseStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(responseStream));
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/jenkins/request-token"));

        servlet =
                new RequestTokenRestEndpoint(validator, consumerStore, factory, tokenStore);
    }

    @Test
    public void verifyThatRequestTokenIsGeneratedStoredAndSentInResponse() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{"http://consumer/callback"}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);
        when(tokenStore.put(UNAUTHORIZED_REQUEST_TOKEN)).thenReturn(UNAUTHORIZED_REQUEST_TOKEN);
        when(factory.generateRequestToken(same(RSA_CONSUMER), eq(URI.create("http://consumer/callback"))))
                .thenReturn(UNAUTHORIZED_REQUEST_TOKEN);

        servlet.handleRequestToken(request, response);

        verify(tokenStore).put(same(UNAUTHORIZED_REQUEST_TOKEN));
        verify(response).setContentType("text/plain");
        assertThat(responseStream.toString(), allOf(
                containsString("oauth_token=1234"),
                containsString("oauth_token_secret=5678"),
                containsString("oauth_callback_confirmed=true")
        ));
    }

    @Test
    public void verifyThatConsumerKeyUnknownResponseIsSentForInvalidConsumerKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{"invalid-consumer-key"},
                OAUTH_CALLBACK, new String[]{"http://consumer/callback"}
        ));

        servlet.handleRequestToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=consumer_key_unknown")));
    }

    @Test
    public void verifyThatSignatureInvalidResponseIsSentWhenThereIsAProblemValidatingTheMessage() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{"http://consumer/callback"}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);
        doThrow(new OAuthProblemException("signature_invalid"))
                .when(validator).validateMessage(isA(OAuthMessage.class), isA(OAuthAccessor.class));

        servlet.handleRequestToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), is(equalTo("oauth_problem=signature_invalid")));
    }

    @Test
    public void verifyThatParameterRejectedResponseIsSentWhenOAuthCallbackIsNotAValidUri() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        final String callbackUrl = "an invalid uri";
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{callbackUrl}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);

        servlet.handleRequestToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=parameter_rejected"));
        assertThat(URLDecoder.decode(responseStream.toString(), "UTF-8"),
                containsString(String.format(INVALID_CALLBACK_ADVICE, callbackUrl)));
    }

    @Test
    public void verifyThatAnOutOfBandCallbackValueIsAcceptedAndRequestTokenReturned() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{"oob"}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);
        when(tokenStore.put(UNAUTHORIZED_REQUEST_TOKEN)).thenReturn(UNAUTHORIZED_REQUEST_TOKEN);
        when(factory.generateRequestToken(same(RSA_CONSUMER))).thenReturn(UNAUTHORIZED_REQUEST_TOKEN);

        servlet.handleRequestToken(request, response);

        verify(tokenStore).put(same(UNAUTHORIZED_REQUEST_TOKEN));
        verify(response).setContentType("text/plain");
        assertThat(responseStream.toString(), allOf(
                containsString("oauth_token=1234"),
                containsString("oauth_token_secret=5678"),
                containsString("oauth_callback_confirmed=true")
        ));
    }

    @Test
    public void verifyThatParameterRejectedResponseIsSentWhenOAutCallbackIsNotAnAbsoluteUri() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        final String callbackUrl = "/path/to/callback";
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{callbackUrl}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);

        servlet.handleRequestToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=parameter_rejected"));
        assertThat(URLDecoder.decode(responseStream.toString(), "UTF-8"),
                containsString(String.format(INVALID_CALLBACK_ADVICE, callbackUrl)));
    }

    @Test
    public void verifyThatParameterRejectedResponseIsSentWhenOAutCallbackDoesNotHaveAnHttpOrHttpsScheme() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        final String callbackUrl = "ftp://crazy/callback";
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_CONSUMER_KEY, new String[]{RSA_CONSUMER.getKey()},
                OAUTH_CALLBACK, new String[]{callbackUrl}
        ));
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(RSA_CONSUMER);

        servlet.handleRequestToken(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).addHeader(eq("WWW-Authenticate"), startsWith("OAuth"));
        verify(response).setContentType(startsWith(OAuth.FORM_ENCODED));
        assertThat(responseStream.toString(), containsString("oauth_problem=parameter_rejected"));
        assertThat(URLDecoder.decode(responseStream.toString(), "UTF-8"),
                containsString(String.format(INVALID_CALLBACK_ADVICE, callbackUrl)));
    }

    private Map<String, String[]> mapOf(String k1, String[] v1, String k2, String[] v2) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }
}