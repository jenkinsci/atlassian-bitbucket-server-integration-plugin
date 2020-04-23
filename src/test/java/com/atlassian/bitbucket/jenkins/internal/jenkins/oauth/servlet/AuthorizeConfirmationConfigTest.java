package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Randomizer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet.AuthorizeConfirmationConfig.*;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsAuthWrapper;
import hudson.model.Descriptor.FormException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.commons.text.TextStringBuilder;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.DEFAULT_REQUEST_TOKEN_TTL;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Tokens.*;
import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet.AuthorizeConfirmationConfig.*;
import static java.lang.String.format;
import static net.oauth.OAuth.OAUTH_CALLBACK;
import static net.oauth.OAuth.OAUTH_TOKEN;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizeConfirmationConfigTest {

    private static final String TOKEN_VALUE = "1234";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    private ServiceProviderTokenStore serviceProviderTokenStore;
    @Mock
    private Randomizer randomizer;
    @Mock
    private Clock clock;
    @Mock
    private StaplerRequest request;
    @Mock
    private StaplerResponse response;
    @Mock
    private JenkinsAuthWrapper jenkinsAuthWrapper;
    private Authentication authentication = new UsernamePasswordAuthenticationToken("test", "test");
    private TextStringBuilder stringBuilder = new TextStringBuilder();

    @Before
    public void setup() throws IOException, ServletException {
        when(request.getRequestURL()).thenReturn(new StringBuffer("htpp://localhost:8080/jenkins"));
        when(request.getParameterMap()).thenReturn(mapOf(
                OAUTH_TOKEN, new String[]{TOKEN_VALUE}));
        when(jenkinsAuthWrapper.getAuthentication()).thenReturn(authentication);
        when(response.getWriter()).thenReturn(new PrintWriter(stringBuilder.asWriter()));
        when(randomizer.randomAlphanumericString(anyInt())).thenReturn(VERIFIER);

        JSONObject jsonObject = new JSONObject();
        Map<String, String> p = new HashMap<>();
        p.put(OAUTH_TOKEN_PARAM, TOKEN_VALUE);
        p.put(OAUTH_CALLBACK, "");
        jsonObject.accumulateAll(p);
        when(request.getSubmittedForm()).thenReturn(jsonObject);
    }

    @Test
    public void throwsExceptionForInvalidToken() throws FormException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();

        expectedException.expect(FormException.class);
        descriptor.createInstance(request);
    }

    @Test
    public void tokenOtherThanRequestTokenAreRejected() throws FormException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(ACCESS_TOKEN));

        expectedException.expect(FormException.class);
        expectedException.expectMessage("token_rejected");
        descriptor.createInstance(request);
    }

    @Test
    public void tokenOnceUsedCannotBeUsedAgain() throws FormException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(AUTHORIZED_REQUEST_TOKEN));

        expectedException.expect(FormException.class);
        expectedException.expectMessage("token_used");
        descriptor.createInstance(request);
    }

    @Test
    public void expiredTokenCannotBeUsed() throws FormException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        ServiceProviderToken unAuthorizedRequestToken =
                ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret("5678").consumer(RSA_CONSUMER).build();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(unAuthorizedRequestToken));
        when(clock.millis()).thenReturn(new Date().getTime() + DEFAULT_REQUEST_TOKEN_TTL + 1);

        expectedException.expect(FormException.class);
        expectedException.expectMessage("token_expired");
        descriptor.createInstance(request);
    }

    @Test
    public void doSubmitReturnsErrorForUnAuthenticatedUser() throws FormException, IOException, ServletException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        ServiceProviderToken unAuthorizedRequestToken =
                ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret("5678").consumer(RSA_CONSUMER).build();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(unAuthorizedRequestToken));
        when(jenkinsAuthWrapper.getAuthentication()).thenReturn(Jenkins.ANONYMOUS);

        AuthorizeConfirmationConfig config = descriptor.createInstance(request);

        HttpResponse r = config.doPerformSubmit(request);
        r.generateResponse(request, response, null);

        verify(response).setStatus(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void submitWithAuthorizeReturnsValidator() throws FormException, IOException, ServletException {
        ServiceProviderToken unAuthorizedRequestToken =
                ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret("5678").consumer(RSA_CONSUMER).build();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(unAuthorizedRequestToken));

        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        AuthorizeConfirmationConfig config = descriptor.createInstance(request);

        when(request.getParameterMap()).thenReturn(
                mapOf(ALLOW_KEY, new String[0],
                        OAUTH_TOKEN, new String[]{TOKEN_VALUE}));
        HttpResponse r = config.doPerformSubmit(request);
        r.generateResponse(request, response, null);

        verify(response).sendRedirect(HttpStatus.SC_MOVED_TEMPORARILY, format("?oauth_token=%s&oauth_verifier=%s", TOKEN_VALUE, VERIFIER));
    }

    @Test
    public void submitWithDenyReturnsValidator() throws FormException, IOException, ServletException {
        ServiceProviderToken unAuthorizedRequestToken =
                ServiceProviderToken.newRequestToken(TOKEN_VALUE).tokenSecret("5678").consumer(RSA_CONSUMER).build();
        when(serviceProviderTokenStore.get(TOKEN_VALUE)).thenReturn(Optional.of(unAuthorizedRequestToken));

        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
        AuthorizeConfirmationConfig config = descriptor.createInstance(request);

        when(request.getParameterMap()).thenReturn(
                mapOf(DENY_KEY, new String[0],
                        OAUTH_TOKEN, new String[]{TOKEN_VALUE}));
        HttpResponse r = config.doPerformSubmit(request);
        r.generateResponse(request, response, null);

        verify(response).sendRedirect(HttpStatus.SC_MOVED_TEMPORARILY,
                format("?oauth_token=1234&oauth_verifier=denied", TOKEN_VALUE, VERIFIER));
    }

    private AuthorizeConfirmationConfigDescriptor createDescriptor() {
        return new AuthorizeConfirmationConfigDescriptor(jenkinsAuthWrapper, serviceProviderTokenStore, randomizer, clock);
    }

    private Map<String, String[]> mapOf(String k1, String[] v1) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        return result;
    }

    private Map<String, String[]> mapOf(String k1, String[] v1, String k2, String[] v2) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }
}