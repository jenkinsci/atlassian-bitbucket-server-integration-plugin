package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import okhttp3.HttpUrl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

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
    @Spy
    Cache<HttpUrl, AtlassianServerCapabilities> capabilitiesCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3600000, TimeUnit.MILLISECONDS)
            .build();

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
        AtlassianServerCapabilities result = getServerCapabilities(capabilitiesClient);
        
        assertEquals(newCapabilities, result);
        // Two checks- once as the key of the cache and again when making the GET request
        verify(requestExecutor, times(2)).getBaseUrl();
        verify(requestExecutor).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
    }

    @Test
    public void testGetServerCapabilitiesWithCache() {
        getServerCapabilities(capabilitiesClient);
        AtlassianServerCapabilities result = getServerCapabilities(capabilitiesClient);

        assertEquals(newCapabilities, result);
        verify(requestExecutor, times(1)).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
    }

    @Test
    public void testGetServerCapabilitiesMultipleClients() {
        getServerCapabilities(capabilitiesClient);
        BitbucketCapabilitiesClientImpl newClient = new BitbucketCapabilitiesClientImpl(requestExecutor, capabilitiesCache);
        AtlassianServerCapabilities result = getServerCapabilities(newClient);
        
        assertEquals(newCapabilities, result);
        verify(requestExecutor, times(1)).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
    }
    
    private AtlassianServerCapabilities getServerCapabilities(BitbucketCapabilitiesClient client) {
        BitbucketResponse response = mock(BitbucketResponse.class);
        doReturn(newCapabilities).when(response).getBody();
        doReturn(response).when(requestExecutor).makeGetRequest(
                eq(HttpUrl.parse(BASE_URL + "/rest/capabilities")), eq(AtlassianServerCapabilities.class));
        return client.getServerCapabilities();
    }
}