package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.*;

/**
 * Represents the {@link PageAreaImpl page area} for adding a Bitbucket Server build trigger (i.e. creates a webhook in
 * Bitbucket Server that triggers a build when a new change is pushed to SCM) for {@link WorkflowJob workflow job types}
 * <p>
 * For other job types (e.g. {@link FreeStyleJob free-style jobs}), use {@link BitbucketWebhookTrigger} instead
 *
 * @see Job#addTrigger(Class)
 */
public class WorkflowJobBitbucketWebhookTrigger extends Trigger {

    public WorkflowJobBitbucketWebhookTrigger(Job parent) {
        super(parent, "/properties/org-jenkinsci-plugins-workflow-job-properties-PipelineTriggersJobProperty/triggers/com-atlassian-bitbucket-jenkins-internal-trigger-BitbucketWebhookTriggerImpl");
    }
}
