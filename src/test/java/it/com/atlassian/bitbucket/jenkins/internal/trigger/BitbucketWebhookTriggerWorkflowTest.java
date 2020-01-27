package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;

public class BitbucketWebhookTriggerWorkflowTest extends BitbucketWebhookTriggerImplTest {

    @Before
    @Override
    public void setup() throws Exception {
        scm = new TestScm();
        CpsScmFlowDefinition definition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
        project = (WorkflowJob) Jenkins.get().createProject(new WorkflowJob.DescriptorImpl(),
                "test" + Jenkins.get().getItems().size());
        ((WorkflowJob) project).setDefinition(definition);
        project.onCreatedFromScratch();
        trigger = new BitbucketWebhookTriggerImpl();
        trigger.start(project, true);
    }
}
