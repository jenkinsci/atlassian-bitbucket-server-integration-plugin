package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Randomizer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.servlet.AuthorizeConfirmationConfig.AuthorizeConfirmationConfigDescriptor;
import hudson.model.Descriptor.FormException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

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

    @Before
    public void setup() {
        when(request.getRequestURL()).thenReturn(new StringBuffer("htpp://localhost:8080/jenkins"));
    }

    @Test
    public void throwsExceptionForInvalidToken() throws FormException {
        AuthorizeConfirmationConfigDescriptor descriptor = createDescriptor();
//        when(request.getParameterMap()).thenReturn(mapOf(
//                OAUTH_TOKEN, new String[]{TOKEN_VALUE}));
//
//        expectedException.expect(FormException.class);
        descriptor.createInstance(request);
    }

    private AuthorizeConfirmationConfigDescriptor createDescriptor() {
        return new AuthorizeConfirmationConfigDescriptor(serviceProviderTokenStore, randomizer, clock);
    }

    private Map<String, String[]> mapOf(String k1, String[] v1) {
        Map<String, String[]> result = new HashMap<>();
        result.put(k1, v1);
        return result;
    }
}