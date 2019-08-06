package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMDescriptorTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());
    private static String SERVER_ID_INVALID = "ServerID_Invalid";
    private static String SERVER_ID_VALID = "ServerID_Valid";
    private static String SERVER_NAME_INVALID = "ServerName_Invalid";
    private static String SERVER_NAME_VALID = "ServerName_Valid";
    @Mock
    private BitbucketClientFactoryProvider clientFactoryProvider;
    @InjectMocks
    private BitbucketSCM.DescriptorImpl descriptor;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private BitbucketServerConfiguration serverConfigurationInvalid;
    @Mock
    private BitbucketServerConfiguration serverConfigurationValid;

    @Before
    public void setup() {
        when(serverConfigurationValid.getId()).thenReturn(SERVER_ID_VALID);
        when(serverConfigurationValid.getServerName()).thenReturn(SERVER_NAME_VALID);
        when(serverConfigurationValid.validate()).thenReturn(FormValidation.ok());
        when(serverConfigurationInvalid.getId()).thenReturn(SERVER_ID_INVALID);
        when(serverConfigurationInvalid.getServerName()).thenReturn(SERVER_NAME_INVALID);
        when(serverConfigurationInvalid.validate()).thenReturn(FormValidation.error("ERROR"));
    }

    @Test
    public void testFillServerIdItemsEmptyId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems("");
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsEmptyList() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.emptyList());
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContainsEmptyValue(model));
    }

    @Test
    public void testFillServerIdItemsMatchingInvalidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_INVALID);
        assertEquals(model.size(), 2);
        assertTrue(modelContains(model, serverConfigurationInvalid, true));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsNullId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(null);
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsValidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContains(model, serverConfigurationValid, true));
    }

    @Test
    public void testNonEmptyRepositorySlug() {
        assertEquals(Kind.OK, descriptor.doCheckRepositorySlug("repo").kind);
    }

    @Test
    public void testProjectKeyEmpty() {
        assertEquals(Kind.ERROR, descriptor.doCheckProjectKey("").kind);
    }

    @Test
    public void testProjectKeyNonEmpty() {
        assertEquals(Kind.OK, descriptor.doCheckProjectKey("PROJECT").kind);
    }

    @Test
    public void testProjectKeyNull() {
        assertEquals(Kind.ERROR, descriptor.doCheckProjectKey(null).kind);
    }

    @Test
    public void testRepositorySlugEmpty() {
        assertEquals(Kind.ERROR, descriptor.doCheckRepositorySlug("").kind);
    }

    @Test
    public void testRepositorySlugNull() {
        assertEquals(Kind.ERROR, descriptor.doCheckRepositorySlug(null).kind);
    }

    @Test
    public void testServerIDNonMatching() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(SERVER_ID_INVALID).kind);
    }

    @Test
    public void testServerIdEmpty() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId("").kind);
    }

    @Test
    public void testServerIdInvalidInList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(true);
        assertEquals(Kind.WARNING, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testServerIdNoList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.emptyList());
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testServerIdNull() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(null).kind);
    }

    @Test
    public void testServerIdValid() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(false);
        assertEquals(Kind.OK, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    //Checks for the presence of an option that matches the one on calling model.includeEmptyValue()
    private boolean modelContainsEmptyValue(StandardListBoxModel model) {
        return model.stream().anyMatch(option ->
                option.name.equals("- none -") && isEmpty(option.value));
    }

    //Checks for the presence of an option that matches one created from a server configuration
    private boolean modelContains(StandardListBoxModel model, BitbucketServerConfiguration configuration,
                                  boolean selected) {
        return model.stream().anyMatch(option -> option.value.equals(configuration.getId()) &&
                                                 option.name.equals(configuration.getServerName()) &&
                                                 option.selected == selected);
    }
}
