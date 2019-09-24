package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Optional.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("ThrowableNotThrown")
@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMDescriptorTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins = new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());

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

    @Test
    public void testDoCheckCredentialsId() {
        descriptor.doCheckCredentialsId("myCredentialsId");
        verify(formValidation).doCheckCredentialsId("myCredentialsId");
    }

    @Test
    public void testDoCheckProjectName() {
        descriptor.doCheckProjectName("myServerId", "myCredentialsId", "myProjectName");
        verify(formValidation).doCheckProjectName("myServerId", "myCredentialsId", "myProjectName");
    }

    @Test
    public void testDoCheckRepositoryName() {
        descriptor.doCheckRepositoryName("myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
        verify(formValidation).doCheckRepositoryName("myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
    }

    @Test
    public void testDoCheckServerId() {
        descriptor.doCheckServerId("myServerId");
        verify(formValidation).doCheckServerId("myServerId");
    }

    @Test
    public void testDoFillCredentialsIdItems() {
        descriptor.doFillCredentialsIdItems("myBaseUrl", "myCredentialsId");
        verify(formFill).doFillCredentialsIdItems("myBaseUrl", "myCredentialsId");
    }

    @Test
    public void testDoFillProjectNameItems() {
        descriptor.doFillProjectNameItems("myServerId", "myCredentialsId", "myProjectName");
        verify(formFill).doFillProjectNameItems("myServerId", "myCredentialsId", "myProjectName");
    }

    @Test
    public void testDoFillRepositoryNameItems() {
        descriptor.doFillRepositoryNameItems("myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
        verify(formFill).doFillRepositoryNameItems("myServerId", "myCredentialsId", "myProjectName", "myRepositoryName");
    }

    @Test
    public void testDoFillServerIdItems() {
        descriptor.doFillServerIdItems("myServerId");
        verify(formFill).doFillServerIdItems("myServerId");
    }

    @Test
    public void testGetBitbucketScmHelper() {
        BitbucketServerConfiguration serverConf = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById("myServerId")).thenReturn(of(serverConf));
        Optional<BitbucketScmHelper> bitbucketScmHelper = descriptor.getBitbucketScmHelper("myServerId", "myCredentialsId");
        assertThat(bitbucketScmHelper.isPresent(), equalTo(true));
        assertThat(bitbucketScmHelper.get().getServerConfiguration(), equalTo(serverConf));
    }
}
