package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.*;
import org.openqa.selenium.support.ui.Select;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the {@link PageAreaImpl page area} for configuring a job to use a Bitbucket Server SCM
 *
 * @see Job#useScm(Class)
 */
@Describable("Bitbucket Server")
public class BitbucketScmConfig extends Scm {
    private static final Logger LOGGER = Logger.getLogger(BitbucketScmWorkflowJob.class.getName());
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
        LOGGER.log(Level.INFO, "Zero:");
        scrollIntoView(this.credentialsId);
        LOGGER.log(Level.INFO, "One:");
        Select select = new Select(this.credentialsId.resolve());
        LOGGER.log(Level.INFO, "Two:");
        select.selectByValue(credentialsId);
        LOGGER.log(Level.INFO, "Three:");
        return this;
    }

    public BitbucketScmConfig sshCredentialsId(String sshCredentialsId) {
        this.scrollIntoView(this.sshCredentialsId);
        new Select(this.sshCredentialsId.resolve()).selectByValue(sshCredentialsId);
        return this;
    }

    public BitbucketScmConfig serverId(String serverId) {
        scrollIntoView(this.serverId);
        new Select(this.serverId.resolve()).selectByVisibleText(serverId);
        return this;
    }

    public BitbucketScmConfig projectName(String projectName) {
        return scrollIntoViewAndSet(projectName, this.projectName);
    }

    public BitbucketScmConfig repositoryName(String repositoryName) {
        return scrollIntoViewAndSet(repositoryName, this.repositoryName);
    }

    public BitbucketScmConfig branchName(String branchName) {
        return scrollIntoViewAndSet(branchName, this.branchName);
    }

    public BitbucketScmConfig anyBranch() {
        return branchName("");
    }

    private BitbucketScmConfig scrollIntoViewAndSet(String repositoryName, Control element) {
        scrollIntoView(element);
        element.set(repositoryName);
        return this;
    }

    private Object scrollIntoView(Control element) {
        // Scrolls the element to the top of the page, and then scrolls back down by 150px to offset the breadcrumbs
        return element.executeScript("arguments[0].scrollIntoView(true);window.scrollBy(0, -150);", element.resolve());
    }
}
