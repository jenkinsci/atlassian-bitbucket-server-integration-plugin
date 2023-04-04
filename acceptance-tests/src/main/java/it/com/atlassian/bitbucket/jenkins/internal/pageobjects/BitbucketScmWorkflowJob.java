package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.atlassian.plugin.util.WaitUntil;
import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.time.Duration.ofSeconds;

/**
 * A {@link WorkflowJob workflow (a.k.a. pipeline) job} that uses a Bitbucket Server SCM to fetch the
 * {@code Jenkinsfile} (as opposed to having the build script saved in Jenkins)
 */
@Describable("org.jenkinsci.plugins.workflow.job.WorkflowJob")
public class BitbucketScmWorkflowJob extends WorkflowJob {

    private static final Logger LOGGER = Logger.getLogger(BitbucketScmWorkflowJob.class.getName());

    public BitbucketScmWorkflowJob(Injector injector, URL url, String name) {
        super(injector, url, name);
    }

    public BitbucketScmConfig bitbucketScmJenkinsFileSource() {
        select("Pipeline script from SCM");
        select("Bitbucket Server");
        ExpectedCondition<WebElement> refreshed =
                ExpectedConditions.refreshed(ExpectedConditions.presenceOfElementLocated(by.id("yui-gen13")));
        WebDriverWait wait = new WebDriverWait(driver, 5);
        wait.until(refreshed);
        return new BitbucketScmConfig(this, "/definition/scm");
    }

    private void select(final String option) {
        WebElement dropdownOption = find(by.option(option));
        // Get the parent dropdown
        WebElement dropdownBox = dropdownOption
                .findElement(By.xpath("./parent::select[@class='jenkins-select__input dropdownList']"));

        Select dropdownSelect = new Select(dropdownBox);

        dropdownSelect.selectByVisibleText(option);

    }
}
