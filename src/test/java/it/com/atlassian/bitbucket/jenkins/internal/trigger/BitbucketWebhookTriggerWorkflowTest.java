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
        project = jenkinsRule.jenkins.get().createProject(WorkflowJob.class,
                "test" + Jenkins.get().getItems().size());
        CpsScmFlowDefinition definition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
        ((WorkflowJob) project).setDefinition(definition);

        trigger = new BitbucketWebhookTriggerImpl();
        ((WorkflowJob) project).addTrigger(trigger);
        trigger.start(project, true);
    }
}
