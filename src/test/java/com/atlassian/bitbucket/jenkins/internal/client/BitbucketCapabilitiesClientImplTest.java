package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import okhttp3.HttpUrl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketCapabilitiesClientImplTest {

    private static final String BASE_URL = "http://example.domain.org:7990/bitbucket";
    @InjectMocks
    private BitbucketCapabilitiesClientImpl capabilitiesClient;
    @Mock
    private AtlassianServerCapabilities newCapabilities;
    @Mock
    private BitbucketRequestExecutor requestExecutor;

    @Before
    public void setup() {
        doReturn(HttpUrl.parse(BASE_URL)).when(requestExecutor).getBaseUrl();
    }

    @Test(expected = BitbucketClientException.class)
    public void testGetServerCapabilitiesExceptionFromSupplier() {
        doThrow(new BitbucketClientException("Client exception")).when(requestExecutor).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
        capabilitiesClient.getServerCapabilities();
    }

    @Test
    public void testGetServerCapabilitiesNoCache() {
        BitbucketResponse response = mock(BitbucketResponse.class);
        doReturn(newCapabilities).when(response).getBody();
        doReturn(response).when(requestExecutor).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));

        assertEquals(newCapabilities, capabilitiesClient.getServerCapabilities());
        verify(requestExecutor).getBaseUrl();
        verify(requestExecutor).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
    }

    @Test
    public void testGetServerCapabilitiesWithCache() {
        testGetServerCapabilitiesNoCache();

        assertEquals(newCapabilities, capabilitiesClient.getServerCapabilities());
        verifyNoMoreInteractions(requestExecutor);
    }
    
    @Test
    public void testGetServerCapabilitiesMultipleClients() {
        testGetServerCapabilitiesNoCache();
        
        BitbucketCapabilitiesClientImpl newClient = new BitbucketCapabilitiesClientImpl(requestExecutor);
        assertEquals(newCapabilities, capabilitiesClient.getServerCapabilities());
        verifyNoMoreInteractions(requestExecutor);
    }
}