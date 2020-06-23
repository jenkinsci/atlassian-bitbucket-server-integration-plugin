package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.supply.BitbucketCapabilitiesSupplier;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketCapabilitiesClientImplTest {

    @Mock
    private BitbucketCapabilitiesSupplier capabilitiesSupplier;
    @Mock
    private AtlassianServerCapabilities newCapabilities, cachedCapabilities;
    @InjectMocks
    private BitbucketCapabilitiesClientImpl capabilitiesClient;

    @Test(expected = BitbucketClientException.class)
    public void testGetServerCapabilitiesExceptionFromSupplier() {
        doThrow(new BitbucketClientException("Client exception")).when(capabilitiesSupplier).get();
        capabilitiesClient.getServerCapabilities();
    }

    @Test
    public void testGetServerCapabilitiesNoCache() {
        when(capabilitiesSupplier.get()).thenReturn(newCapabilities);
        assertEquals(newCapabilities, capabilitiesClient.getServerCapabilities());
        verify(capabilitiesSupplier).get();
    }

    @Test
    public void testGetServerCapabilitiesWithCache() {
        when(capabilitiesSupplier.get()).thenReturn(cachedCapabilities);
        capabilitiesClient.getServerCapabilities();
        verify(capabilitiesSupplier).get();

        assertEquals(cachedCapabilities, capabilitiesClient.getServerCapabilities());
        verifyNoMoreInteractions(capabilitiesSupplier);
    }
}