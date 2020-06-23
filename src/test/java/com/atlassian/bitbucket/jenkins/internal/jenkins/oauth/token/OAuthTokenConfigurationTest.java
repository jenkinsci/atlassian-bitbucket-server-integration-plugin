package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsAuthWrapper;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Tokens.*;
import static com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token.OAuthTokenConfiguration.REVOKE_BUTTON_NAME;
import static java.lang.String.format;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OAuthTokenConfigurationTest {

    private static final String TEST_USER = "test";

    @Mock
    private JenkinsAuthWrapper jenkinsAuthWrapper;
    @Mock
    private Clock clock;
    @Mock
    private ServiceProviderTokenStore store;
    @Mock
    private StaplerRequest request;
    @Mock
    private StaplerResponse response;
    private Authentication authentication = new UsernamePasswordAuthenticationToken(TEST_USER, "test");

    private OAuthTokenConfiguration instance;

    @Before
    public void setup() throws IOException {
        instance = new OAuthTokenConfiguration(jenkinsAuthWrapper, clock, store);
        when(jenkinsAuthWrapper.getAuthentication()).thenReturn(authentication);
    }

    @Test
    public void returnsTokenForTheLoggedInUser() {
        ServiceProviderToken token1 = createAccessTokenForUser(TEST_USER);
        ServiceProviderToken token2 = createAccessTokenForUser(TEST_USER);
        when(store.getAccessTokensForUser(TEST_USER)).thenReturn(Arrays.asList(token1, token2));

        List<DisplayAccessToken> tokens = instance.getTokens();

        List<String> tokenName = tokens.stream().map(DisplayAccessToken::getToken).collect(Collectors.toList());

        assertThat(tokenName, iterableWithSize(2));
        assertThat(tokenName, hasItems(token1.getToken(), token2.getToken()));
    }

    @Test
    public void revokeAndSuccessfulRedirect() throws IOException, ServletException {
        when(request.getParameterMap()).thenReturn(
                mapOf(TOKEN_VALUE, new String[]{REVOKE_BUTTON_NAME}));

        HttpResponse r = instance.doRevoke(request);
        r.generateResponse(request, response, null);

        verify(store).remove(TOKEN_VALUE);
        verify(response).sendRedirect(HttpStatus.SC_MOVED_TEMPORARILY,
                format(".", TOKEN_VALUE, VERIFIER));
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