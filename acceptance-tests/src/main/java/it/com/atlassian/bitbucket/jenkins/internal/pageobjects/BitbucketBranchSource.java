package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.plugins.workflow_multibranch.BranchSource;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;
import org.jenkinsci.test.acceptance.po.WorkflowMultiBranchJob;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import static it.com.atlassian.bitbucket.jenkins.internal.util.BrowserUtils.runWithRetry;

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

    public BitbucketBranchSource discoverBranches(boolean isDiscoveryEnabled) {
        if (isDiscoveryEnabled) {
            addTrait("Discover branches");
        } else {
            removeTraitIfEnabled("jenkins.plugins.git.traits.BranchDiscoveryTrait");
        }

        return this;
    }

    public BitbucketBranchSource discoverPullRequests(boolean isDiscoveryEnabled) {
        if (isDiscoveryEnabled) {
            addTrait("Discover pull requests");
        } else {
            removeTraitIfEnabled("com.atlassian.bitbucket.jenkins.internal.scm.BitbucketPullRequestDiscoveryTrait");
        }

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

    private void addTrait(String name) {
        control(By.cssSelector(".trait-container .trait-add")).click();
        WebElement traitOption =  control(By.linkText(name)).resolve();
        if (traitOption.isEnabled()) {
            runWithRetry(traitOption::click);
        }
    }

    private void removeTraitIfEnabled(String traitClassName) {
        try {
            WebElement removeButton = control(By.cssSelector("div[descriptorid='" + traitClassName +
                    "'] button.repeatable-delete")).resolve();

            if (removeButton.isEnabled()) {
                runWithRetry(removeButton::click);
            }
        } catch (NoSuchElementException e) {
            // Do nothing
        }
    }
}
