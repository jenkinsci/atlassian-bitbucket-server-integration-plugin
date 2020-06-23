package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;
import org.jenkinsci.test.acceptance.po.Trigger;

/**
 * Represents the {@link PageAreaImpl page area} for adding a Bitbucket Server build trigger (i.e. creates a webhook in
 * Bitbucket Server that triggers a build when a new change is pushed to SCM)
 *
 * @see Job#addTrigger(Class)
 */
public class BitbucketWebhookTrigger extends Trigger {

    public BitbucketWebhookTrigger(Job parent) {
        super(parent, "/com-atlassian-bitbucket-jenkins-internal-trigger-BitbucketWebhookTriggerImpl");
    }
}
