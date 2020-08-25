package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.scm.SCMDescriptor;
import jenkins.model.Jenkins;

public class TestSCM extends BitbucketSCM {

    public TestSCM(BitbucketSCM bitbucketSCM) {
        super(bitbucketSCM.getId(), bitbucketSCM.getBranches(), bitbucketSCM.getCredentialsId(), bitbucketSCM.getSshCredentialsId(),
                bitbucketSCM.getExtensions(), bitbucketSCM.getGitTool(), bitbucketSCM.getProjectName(),
                bitbucketSCM.getRepositoryName(), bitbucketSCM.getServerId(), bitbucketSCM.getMirrorName());
    }

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return (SCMDescriptor) Jenkins.get().getDescriptorOrDie(BitbucketSCM.class);
    }
}
