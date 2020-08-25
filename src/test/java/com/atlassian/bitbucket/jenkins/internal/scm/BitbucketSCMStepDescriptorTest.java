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
