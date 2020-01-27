package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import hudson.model.FreeStyleProject;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

public class BitbucketWebhookTriggerFreestyleTest extends BitbucketWebhookTriggerImplTest {

    @Before
    @Override
    public void setup() throws Exception {
        project = jenkinsRule.createFreeStyleProject();
        scm = new TestScm();
        ((FreeStyleProject) project).setScm(scm);
        trigger = new BitbucketWebhookTriggerImpl();
        trigger.start(project, true);
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        project.delete();
    }
}
