package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.plugins.workflow_multibranch.BranchSource;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;
import org.jenkinsci.test.acceptance.po.WorkflowMultiBranchJob;
import org.openqa.selenium.support.ui.Select;

/**
 * Represents the {@link PageAreaImpl page area} for setting up a Bitbucket Server Branch Source in Jenkins
 *
 * @see WorkflowMultiBranchJob#addBranchSource(Class)
 */
@Describable("Bitbucket server")
public class BitbucketBranchSource extends BranchSource {

    private final Control credentialsId = control("credentialsId");
    private final Control serverId = control("serverId");
    private final Control projectName = control("projectName");
    private final Control repositoryName = control("repositoryName");

    public BitbucketBranchSource(WorkflowMultiBranchJob job, String path) {
        super(job, path);
    }

    public BitbucketBranchSource credentialsId(String credentialsId) {
        new Select(this.credentialsId.resolve()).selectByValue(credentialsId);
        return this;
    }

    public BitbucketBranchSource serverId(String serverId) {
        new Select(this.serverId.resolve()).selectByVisibleText(serverId);
        return this;
    }

    public BitbucketBranchSource projectName(String projectName) {
        this.projectName.set(projectName);
        return this;
    }

    public BitbucketBranchSource repositoryName(String repositoryName) {
        this.repositoryName.set(repositoryName);
        return this;
    }
}
