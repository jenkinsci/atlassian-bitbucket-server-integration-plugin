package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.OAuthRequestUtils;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.AuthenticationFailedException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.OAuth1Authenticator;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.SecurityModeChecker;
import hudson.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.OAuth1aRequestFilter.OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OauthCrumbExclusionTest {

    @Mock
    private OAuth1Authenticator authenticator;
    private List<String> buildUrls = new ArrayList<>();
    @Spy
    private FilterChain chain;
    private OauthCrumbExclusion crumbExclusion;
    @Mock
    private OAuthRequestUtils oAuthRequestUtils;
    @Mock
    private HttpServletRequest request;
    @Spy
    private HttpServletResponse response;
    @Mock
    private SecurityModeChecker securityChecker;

    @Before
    public void setUp() throws Exception {
        when(securityChecker.isSecurityEnabled()).thenReturn(true);
        when(oAuthRequestUtils.isOAuthAccessAttempt(any())).thenReturn(true);
        crumbExclusion = new OauthCrumbExclusion(authenticator, securityChecker, oAuthRequestUtils) {
            @Override
            List<String> getBuilds() {
                return buildUrls;
            }
        };
    }

    @Test
    public void shouldAllowBuildStartEndpoint() throws ServletException, IOException, AuthenticationFailedException {
        when(request.getPathInfo()).thenReturn("/job/this/is/my/build/build");
        when(authenticator.authenticate(request, response)).thenReturn(mock(User.class));
        buildUrls.add("/job/this/is/my/build/build");

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(request).setAttribute(OAUTH_REQUEST_AUTHENTICATED_ATTRIBUTE_KEY, true);
        verify(chain).doFilter(request, response);
    }

    @Test
    public void testShouldContinueFilterOtherEndpoints() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/jenkins-endpoint");

        assertFalse(crumbExclusion.process(request, response, chain));

        verifyNoInteractions(chain);
    }

    @Test
    public void testShouldExcludeAccessTokenEndpoint() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn(OAuthRequestUtils.EXCLUSION_PATH + AccessTokenRestEndpoint.ACCESS_TOKEN_PATH_END);

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(chain).doFilter(request, response);
    }

    @Test
    public void testShouldExcludeRequestTokenEndpoint() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn(OAuthRequestUtils.EXCLUSION_PATH + RequestTokenRestEndpoint.REQUEST_TOKEN_PATH_END);

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(chain).doFilter(request, response);
    }
}