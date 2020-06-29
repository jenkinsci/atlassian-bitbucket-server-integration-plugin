package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Item;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMStepDescriptorTest {

    @InjectMocks
    BitbucketSCMStep.DescriptorImpl descriptor;

    @Mock
    private BitbucketScmFormFillDelegate formFill;
    @Mock
    private BitbucketScmFormValidationDelegate formValidation;
    @Mock
    private Item item;

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
        descriptor.doFillCredentialsIdItems(item, "myBaseUrl", "myCredentialsId");
        verify(formFill).doFillCredentialsIdItems(item, "myBaseUrl", "myCredentialsId");
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
}
