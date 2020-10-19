package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.*;
import org.openqa.selenium.support.ui.Select;

/**
 * Represents the {@link PageAreaImpl page area} for configuring a job to use a Bitbucket Server SCM
 *
 * @see Job#useScm(Class)
 */
@Describable("Bitbucket Server")
public class BitbucketScmConfig extends Scm {

    private final Control sshCredentialsId = control("sshCredentialsId");
    private final Control credentialsId = control("credentialsId");
    private final Control serverId = control("serverId");
    private final Control projectName = control("projectName");
    private final Control repositoryName = control("repositoryName");
    private final Control branchName = control("branches/name");

    public BitbucketScmConfig(Job job, String path) {
        super(job, path);
    }

    public BitbucketScmConfig credentialsId(String credentialsId) {
        new Select(this.credentialsId.resolve()).selectByValue(credentialsId);
        return this;
    }

    public BitbucketScmConfig sshCredentialsId(String sshCredentialsId) {
        new Select(this.sshCredentialsId.resolve()).selectByValue(sshCredentialsId);
        return this;
    }

    public BitbucketScmConfig serverId(String serverId) {
        new Select(this.serverId.resolve()).selectByVisibleText(serverId);
        return this;
    }

    public BitbucketScmConfig projectName(String projectName) {
        this.projectName.set(projectName);
        return this;
    }

    public BitbucketScmConfig repositoryName(String repositoryName) {
        this.repositoryName.set(repositoryName);
        return this;
    }

    public BitbucketScmConfig branchName(String branchName) {
        this.branchName.set(branchName);
        return this;
    }

    public BitbucketScmConfig anyBranch() {
        return branchName("");
    }
}
