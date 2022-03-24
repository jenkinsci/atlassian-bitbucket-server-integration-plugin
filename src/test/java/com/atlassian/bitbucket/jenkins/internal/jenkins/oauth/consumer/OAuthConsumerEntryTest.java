package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.access.AccessDeniedException;

import javax.servlet.ServletException;
import java.net.URISyntaxException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OAuthConsumerEntryTest {

    private static final String CONSUMER_CALLBACKURL_FIELD = "callbackUrl";
    private static final String CONSUMER_KEY_FIELD = "consumerKey";
    private static final String CONSUMER_NAME_FIELD = "consumerName";
    private static final String CONSUMER_SECRET_FIELD = "consumerSecret";
    private static final String KEY_ERROR_MESSAGE = "Consumer key cannot be empty";
    private static final String KEY_EXISTS_ERROR = "Key with the same name already exists";
    private static final String NAME_ERROR_MESSAGE = "Consumer name cannot be empty";
    private static final String SECRET_ERROR_MESSAGE = "Consumer secret cannot be empty";
    private static final String URL_ERROR_MESSAGE =
            "This isn&#039;t a valid URL. Check for typos and make sure to include http:// or https://";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    ServiceProviderConsumerStore consumerStore = mock(ServiceProviderConsumerStore.class);
    @Mock
    Jenkins jenkins;
    @Mock
    JenkinsProvider jenkinsProvider = mock(JenkinsProvider.class);
    @InjectMocks
    private OAuthConsumerEntry.OAuthConsumerEntryDescriptor oAuthConsumerEntryDescriptor;

    @Before
    public void setup() {
        when(jenkinsProvider.get()).thenReturn(jenkins);
    }

    @Test
    public void testDoCheckConsumerKey() {
        Consumer consumer =
                new Consumer.Builder("NonExistantKey").name("name").signatureMethod(Consumer.SignatureMethod.HMAC_SHA1).build();
        when(consumerStore.get(any(String.class))).thenReturn(Optional.of(consumer));
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerKey("Key1");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), KEY_EXISTS_ERROR);
    }

    @Test
    public void testDoCheckConsumerKeyWithDash() {
        Consumer consumer =
                new Consumer.Builder("NonExistantKey").name("name").signatureMethod(Consumer.SignatureMethod.HMAC_SHA1).build();
        when(consumerStore.get(any(String.class))).thenReturn(Optional.empty());
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerKey("Key-with-dash");
        assertEquals(formValidation.kind, FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckConsumerKeyWithSameName() {
        Consumer consumer =
                new Consumer.Builder("ExistantKey").name("name").signatureMethod(Consumer.SignatureMethod.HMAC_SHA1).build();
        when(consumerStore.get(any(String.class))).thenReturn(Optional.of(consumer));
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerKey("ExistantKey");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), KEY_EXISTS_ERROR);
    }

    @Test
    public void testDoCheckConsumerKeyWithSpaces() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerKey("Key with spaces");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), KEY_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckConsumerName() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerName("Name1");
        assertEquals(formValidation.kind, FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckConsumerSecret() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerSecret("Secret1");
        assertEquals(formValidation.kind, FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckEmptyConsumerKey() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerKey("");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), KEY_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckEmptyConsumerName() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerName("");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), NAME_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckEmptyConsumerSecret() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckConsumerSecret("");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), SECRET_ERROR_MESSAGE);
    }

    @Test
    public void testDoCheckHttpConsumerUrl() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckCallbackUrl("http://Callback/");
        assertEquals(formValidation.kind, FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckInvalidConsumerUrl() {
        FormValidation formValidation = oAuthConsumerEntryDescriptor.doCheckCallbackUrl("Url1");
        assertEquals(formValidation.kind, FormValidation.Kind.ERROR);
        assertEquals(formValidation.getMessage(), URL_ERROR_MESSAGE);
    }

    @Test
    public void testGetConsumerFromSubmittedForm() throws ServletException, Descriptor.FormException, URISyntaxException {
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        JSONObject body = new JSONObject();
        body.put(CONSUMER_KEY_FIELD, "Key1");
        body.put(CONSUMER_NAME_FIELD, "Name1");
        body.put(CONSUMER_SECRET_FIELD, "Secret1");
        body.put(CONSUMER_CALLBACKURL_FIELD, "http://Url1");
        when(staplerRequest.getSubmittedForm()).thenReturn(body);
        when(consumerStore.get(any(String.class))).thenReturn(Optional.empty());
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }

    @Test
    public void testGetConsumerFromSubmittedFormInvalidKey() throws ServletException, Descriptor.FormException, URISyntaxException {
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        JSONObject body = new JSONObject();
        body.put(CONSUMER_KEY_FIELD, "ExistantKey");
        body.put(CONSUMER_NAME_FIELD, "Name1");
        body.put(CONSUMER_SECRET_FIELD, "Secret1");
        body.put(CONSUMER_CALLBACKURL_FIELD, "http://Url1");
        when(staplerRequest.getSubmittedForm()).thenReturn(body);
        Consumer consumer =
                new Consumer.Builder("ExistantKey").name("name").signatureMethod(Consumer.SignatureMethod.HMAC_SHA1).build();
        when(consumerStore.get(any(String.class))).thenReturn(Optional.of(consumer));
        expectedException.expect(Descriptor.FormException.class);
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }

    @Test
    public void testGetConsumerFromSubmittedFormInvalidName() throws ServletException, Descriptor.FormException, URISyntaxException {
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        JSONObject body = new JSONObject();
        body.put(CONSUMER_KEY_FIELD, "Key1");
        body.put(CONSUMER_NAME_FIELD, "");
        body.put(CONSUMER_SECRET_FIELD, "Secret1");
        body.put(CONSUMER_CALLBACKURL_FIELD, "http://Url1");
        when(staplerRequest.getSubmittedForm()).thenReturn(body);
        when(consumerStore.get(any(String.class))).thenReturn(Optional.empty());
        expectedException.expect(Descriptor.FormException.class);
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }

    @Test
    public void testGetConsumerFromSubmittedFormInvalidPermission() throws ServletException, Descriptor.FormException, URISyntaxException {
        doThrow(AccessDeniedException.class).when(jenkins).checkPermission(any(hudson.security.Permission.class));
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        expectedException.expect(AccessDeniedException.class);
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }

    @Test
    public void testGetConsumerFromSubmittedFormInvalidSecret() throws ServletException, Descriptor.FormException, URISyntaxException {
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        JSONObject body = new JSONObject();
        body.put(CONSUMER_KEY_FIELD, "Key1");
        body.put(CONSUMER_NAME_FIELD, "Name1");
        body.put(CONSUMER_SECRET_FIELD, "");
        body.put(CONSUMER_CALLBACKURL_FIELD, "http://Url1");
        when(staplerRequest.getSubmittedForm()).thenReturn(body);
        when(consumerStore.get(any(String.class))).thenReturn(Optional.empty());
        expectedException.expect(Descriptor.FormException.class);
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }

    @Test
    public void testGetConsumerFromSubmittedFormInvalidUrl() throws ServletException, Descriptor.FormException, URISyntaxException {
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        JSONObject body = new JSONObject();
        body.put(CONSUMER_KEY_FIELD, "Key1");
        body.put(CONSUMER_NAME_FIELD, "");
        body.put(CONSUMER_SECRET_FIELD, "Secret1");
        body.put(CONSUMER_CALLBACKURL_FIELD, "");
        when(staplerRequest.getSubmittedForm()).thenReturn(body);
        when(consumerStore.get(any(String.class))).thenReturn(Optional.empty());
        expectedException.expect(Descriptor.FormException.class);
        oAuthConsumerEntryDescriptor.getConsumerFromSubmittedForm(staplerRequest);
    }
}