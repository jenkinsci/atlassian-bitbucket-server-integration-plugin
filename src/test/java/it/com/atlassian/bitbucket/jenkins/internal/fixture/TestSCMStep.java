package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep;
import hudson.plugins.git.BranchSpec;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.List;

/**
 * A test class to expose the createSCM method publicly for tests
 */
public class TestSCMStep extends BitbucketSCMStep {

    public TestSCMStep(String id, List<BranchSpec> branches, String credentialsId, String sshCredentialsId,
                       String projectName, String repositoryName, String serverId, String mirrorName) {
        super(projectName, repositoryName, serverId);
        // Stapler applies the fields using data-bound setters, so we replicate that behaviour rather than setting manually
        setBranches(branches);
        setCredentialsId(credentialsId);
        setId(id);
        setSshCredentialsId(sshCredentialsId);
        setMirrorName(mirrorName);
    }

    @Override
    public TestSCM createSCM() {
        return new TestSCM((BitbucketSCM) super.createSCM());
    }

    @Override
    public StepDescriptor getDescriptor() {
        return (StepDescriptor) Jenkins.getInstance().getDescriptorOrDie(BitbucketSCMStep.class);
    }
}
