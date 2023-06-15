package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;

/**
 * A {@link WorkflowJob workflow (a.k.a. pipeline) job} that uses a Bitbucket Server SCM to fetch the
 * {@code Jenkinsfile} (as opposed to having the build script saved in Jenkins)
 */
@Describable("org.jenkinsci.plugins.workflow.job.WorkflowJob")
public class BitbucketScmWorkflowJob extends WorkflowJob {

    public BitbucketScmWorkflowJob(Injector injector, URL url, String name) {
        super(injector, url, name);
    }

    public BitbucketScmConfig bitbucketScmJenkinsFileSource() {
        select("Pipeline script from SCM");
        select("Bitbucket Server");

        // Once the "Test Connection" button has loaded on the page, it's safe
        // to select other elements without getting a StaleElementReferenceException
        waitForPresence(by.button("Test connection"));

        return new BitbucketScmConfig(this, "/definition/scm");
    }

    private void select(final String option) {
        // Wait for the option to be present befor clicking it
        By selector = by.option(option);
        waitForPresence(selector);
        find(selector).click();
    }

    private void waitForPresence(By selector) {
        ExpectedCondition<WebElement> refreshed = ExpectedConditions
                .refreshed(ExpectedConditions.presenceOfElementLocated(selector));
        WebDriverWait wait = new WebDriverWait(driver, 5);
        wait.until(refreshed);
    }
}
