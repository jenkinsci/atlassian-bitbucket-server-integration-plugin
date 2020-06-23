package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.*;
import org.openqa.selenium.support.ui.Select;

/**
 * Represents the {@link PageAreaImpl page area} for configuring a job to use a Bitbucket Server SCM
 *
 * @see Job#useScm(Class)
 */
@Describable("Bitbucket Server")
public class BitbucketScm extends Scm {

    private final Control credentialsId = control("credentialsId");
    private final Control serverId = control("serverId");
    private final Control projectName = control("projectName");
    private final Control repositoryName = control("repositoryName");
    private final Control branchName = control("branches/name");

    public BitbucketScm(Job job, String path) {
        super(job, path);
    }

    public BitbucketScm credentialsId(String credentialsId) {
        new Select(this.credentialsId.resolve()).selectByValue(credentialsId);
        return this;
    }

    public BitbucketScm serverId(String serverId) {
        new Select(this.serverId.resolve()).selectByVisibleText(serverId);
        return this;
    }

    public BitbucketScm projectName(String projectName) {
        this.projectName.set(projectName);
        return this;
    }

    public BitbucketScm repositoryName(String repositoryName) {
        this.repositoryName.set(repositoryName);
        return this;
    }

    public BitbucketScm branchName(String branchName) {
        this.branchName.set(branchName);
        return this;
    }

    public BitbucketScm anyBranch() {
        return branchName("");
    }
}
