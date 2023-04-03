package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

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
        return new BitbucketScmConfig(this, "/definition/scm");
    }

    private void select(final String option) {
        WebElement dropdownOption = find(by.option(option));
        waitFor(ExpectedConditions.stalenessOf(dropdownOption));
        dropdownOption = find(by.option(option));
        // Get the parent dropdown
        WebElement dropdownBox = dropdownOption
                .findElement(By.xpath("./parent::select[@class='jenkins-select__input dropdownList']"));
        Select dropdownSelect = new Select(dropdownBox);
        dropdownSelect.selectByVisibleText(option);
    }
}
