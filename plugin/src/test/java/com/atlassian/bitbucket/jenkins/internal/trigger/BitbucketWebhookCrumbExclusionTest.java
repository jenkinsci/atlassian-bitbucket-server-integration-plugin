package com.atlassian.bitbucket.jenkins.internal.trigger;

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

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookCrumbExclusionTest {

    @Spy
    private FilterChain chain;
    private BitbucketWebhookCrumbExclusion crumbExclusion;
    @Mock
    private HttpServletRequest request;
    @Spy
    private HttpServletResponse response;

    @Before
    public void setup() {
        crumbExclusion = new BitbucketWebhookCrumbExclusion();
    }

    @Test
    public void testShouldBlockOtherEndpoints() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/push");

        assertFalse(crumbExclusion.process(request, response, chain));

        verifyZeroInteractions(chain);
    }

    @Test
    public void testShouldExcludeWebhookEndpoint() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn('/' + BIBUCKET_WEBHOOK_URL + "/push");

        assertTrue(crumbExclusion.process(request, response, chain));

        verify(chain).doFilter(request, response);
    }
}
