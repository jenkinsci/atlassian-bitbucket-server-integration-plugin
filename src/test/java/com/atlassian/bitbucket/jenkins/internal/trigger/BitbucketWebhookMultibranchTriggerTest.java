package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookMultibranchTriggerTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();
    @Mock
    private BitbucketServerConfiguration bbsConfig;
    private BitbucketWebhookMultibranchTrigger.DescriptorImpl descriptor;
    @Mock
    private BitbucketPluginConfiguration pluginConfig;
    @Mock
    private RetryingWebhookHandler webhookHandler;

    @Before
    public void setUp() {
        descriptor = new BitbucketWebhookMultibranchTrigger.DescriptorImpl(webhookHandler, pluginConfig);
    }

    @Test
    public void testIsApplicableForMultibranch() {
        BitbucketWebhookMultibranchTrigger.DescriptorImpl descriptor =
                new BitbucketWebhookMultibranchTrigger.DescriptorImpl();
        assertThat(descriptor.isApplicable(mock(MultiBranchProject.class)), is(true));
    }

    @Test
    public void testIsNotApplicableForFreeStyleOrWorkflow() {
        BitbucketWebhookMultibranchTrigger.DescriptorImpl descriptor =
                new BitbucketWebhookMultibranchTrigger.DescriptorImpl();
        assertThat(descriptor.isApplicable(new FreeStyleProject((ItemGroup) null, "name")), is(false));
        assertThat(descriptor.isApplicable(new WorkflowJob(null, "name")), is(false));
    }
}
