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
    
    private final StepDescriptor descriptor;

    protected TestSCMStep(Builder builder) {
        super(builder.projectName, builder.repositoryName);
        setId(builder.id);
        if (builder.descriptor != null) {
            descriptor = builder.descriptor;
        } else {
            descriptor = (StepDescriptor) Jenkins.get().getDescriptorOrDie(BitbucketSCMStep.class);
        }
        if (builder.branches != null) {
            setBranches(builder.branches);
        }
        if (builder.serverId != null) {
            setServerId(builder.serverId);
        }
        if (builder.serverName != null) {
            setServerName(builder.serverName);
        }
        setCredentialsId(builder.credentialsId);
        setSshCredentialsId(builder.sshCredentialsId);
        setMirrorName(builder.mirrorName);
    }
    
    @Override
    public TestSCM createSCM() {
        return new TestSCM((BitbucketSCM) super.createSCM());
    }

    @Override
    public StepDescriptor getDescriptor() {
        return descriptor;
    }
    
    public static class Builder {
        
        private final String id;
        private final String projectName;
        private final String repositoryName;
        private List<BranchSpec> branches;
        private String credentialsId;
        private String sshCredentialsId;
        private String serverId;
        private String serverName;
        private String mirrorName;
        private StepDescriptor descriptor;
        
        public Builder(String id, String projectName, String repositoryName) {
            this.id = id;
            this.projectName = projectName;
            this.repositoryName = repositoryName;
        }
        
        public Builder branches(List<BranchSpec> branches) {
            this.branches = branches;
            return this;
        }
        
        public TestSCMStep build() {
            return new TestSCMStep(this);
        }
        
        public Builder credentialsId(String credentialsId) {
            this.credentialsId = credentialsId;
            return this;
        }
        
        public Builder descriptor(StepDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }
        
        public Builder mirrorName(String mirrorName) {
            this.mirrorName = mirrorName;
            return this;
        }
        
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }
        
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }
        
        public Builder sshCredentialsId(String sshCredentialsId) {
            this.sshCredentialsId = sshCredentialsId;
            return this;
        }
    }
}
