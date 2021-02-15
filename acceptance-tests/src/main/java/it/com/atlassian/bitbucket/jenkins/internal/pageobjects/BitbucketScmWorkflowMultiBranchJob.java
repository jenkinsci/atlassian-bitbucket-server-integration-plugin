package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowMultiBranchJob;
import org.openqa.selenium.NoSuchElementException;

import java.net.URL;

/**
 * A {@link WorkflowMultiBranchJob pipeline multi-branch job} that uses Bitbucket Server as the SCM source
 */
@Describable("org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject")
public class BitbucketScmWorkflowMultiBranchJob extends WorkflowMultiBranchJob {

    private final Control bitbucketWebhookTrigger =
            control("/com-atlassian-bitbucket-jenkins-internal-trigger-BitbucketWebhookMultibranchTrigger");
    private final Control bitbucketWebhookRefTrigger =
            control("/com-atlassian-bitbucket-jenkins-internal-trigger-BitbucketWebhookMultibranchTrigger/refTrigger");
    private final Control bitbucketWebhookPRTrigger =
            control("/com-atlassian-bitbucket-jenkins-internal-trigger-BitbucketWebhookMultibranchTrigger/pullRequestTrigger");

    public BitbucketScmWorkflowMultiBranchJob(Injector injector, URL url, String name) {
        super(injector, url, name);
    }

    // todo: remove and use the `reIndex()` from super type once this is resolved:
    //  https://issues.jenkins-ci.org/browse/JENKINS-63044
    @Override
    public void reIndex() {
        try {
            super.reIndex();
        } catch (NoSuchElementException e) {
            // JENKINS-63044
            find(by.xpath("//div[@class=\"task\"]//*[text()=\"Scan Multibranch Pipeline Now\"]")).click();
        }
    }

    public void enableBitbucketWebhookTrigger(boolean enableRefTrigger, boolean enablePRTrigger) {
        bitbucketWebhookTrigger.check(true);
        bitbucketWebhookRefTrigger.check(enableRefTrigger);
        bitbucketWebhookPRTrigger.check(enablePRTrigger);
    }
}
