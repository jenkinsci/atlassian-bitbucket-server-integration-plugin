package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import hudson.model.Item;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.verify;

@SuppressWarnings("ThrowableNotThrown")
@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMDescriptorTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());

    @InjectMocks
    private BitbucketSCM.DescriptorImpl descriptor;
    @Mock
    private BitbucketScmFormFillDelegate formFill;
    @Mock
    private BitbucketScmFormValidationDelegate formValidation;
    @Mock
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Mock
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    private Item parent;

    @Test
    public void testDoCheckCredentialsId() {
        descriptor.doCheckCredentialsId(parent, "myCredentialsId");
        verify(formValidation).doCheckCredentialsId(parent, "myCredentialsId");
    }

    @Test
    public void testDoCheckProjectName() {
        descriptor.doCheckProjectName(parent, "myServerId", "myCredentialsId", "myProjectName");
        verify(formValidation).doCheckProjectName(parent, "myServerId", "myCredentialsId", "myProjectName");
    }

    @Test
    public void testDoCheckRepositoryName() {
        descriptor.doCheckRepositoryName(parent, "myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
        verify(formValidation).doCheckRepositoryName(parent, "myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
    }

    @Test
    public void testDoCheckServerId() {
        descriptor.doCheckServerId(parent, "myServerId");
        verify(formValidation).doCheckServerId(parent, "myServerId");
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        descriptor.doFillCredentialsIdItems(parent, "myBaseUrl", "myCredentialsId");
        verify(formFill).doFillCredentialsIdItems(parent, "myBaseUrl", "myCredentialsId");
    }

    @Test
    public void testDoFillProjectNameItems() {
        descriptor.doFillProjectNameItems(parent, "myServerId", "myCredentialsId", "myProjectName");
        verify(formFill).doFillProjectNameItems(parent, "myServerId", "myCredentialsId", "myProjectName");
    }

    @Test
    public void testDoFillRepositoryNameItems() {
        descriptor.doFillRepositoryNameItems(parent, "myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
        verify(formFill).doFillRepositoryNameItems(parent, "myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
    }

    @Test
    public void testDoFillServerIdItems() {
        descriptor.doFillServerIdItems(parent, "myServerId");
        verify(formFill).doFillServerIdItems(parent, "myServerId");
    }
}
