package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenCrumbExclusionTest {

    @Spy
    private FilterChain chain;
    private TokenCrumbExclusion crumbExclusion;
    @Mock
    private HttpServletRequest request;
    @Spy
    private HttpServletResponse response;

    @Before
    public void setup() {
        crumbExclusion = new TokenCrumbExclusion();
    }

    @Test
    public void testShouldBlockOtherEndpoints() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/jenkins-endpoint");

        assertFalse(crumbExclusion.process(request, response, chain));

        verifyZeroInteractions(chain);
    }

    @Test
    public void testShouldExcludeAccessTokenEndpoint() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn(TokenCrumbExclusion.EXCLUSION_PATH + AccessTokenRestEndpoint.ACCESS_TOKEN_PATH_END);

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(chain).doFilter(request, response);
    }

    @Test
    public void testShouldExcludeRequestTokenEndpoint() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn(TokenCrumbExclusion.EXCLUSION_PATH + RequestTokenRestEndpoint.REQUEST_TOKEN_PATH_END);

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(chain).doFilter(request, response);
    }
}