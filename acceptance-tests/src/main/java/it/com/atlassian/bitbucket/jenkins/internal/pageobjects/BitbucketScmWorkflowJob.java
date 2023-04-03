package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.atlassian.plugin.util.WaitUntil;
import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

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
        return new BitbucketScmConfig(this, "/definition/scm");
    }

    private void select(final String option) {
        LOGGER.log(Level.INFO, "Zero:");
        WebElement dropdownOption = find(by.option(option));
        LOGGER.log(Level.INFO, "First "  + isStale(dropdownOption));
        // Get the parent dropdown
        WebElement dropdownBox = dropdownOption
                .findElement(By.xpath("./parent::select[@class='jenkins-select__input dropdownList']"));
        LOGGER.log(Level.INFO, "Second "  + isStale(dropdownOption));
        Select dropdownSelect = new Select(dropdownBox);
        LOGGER.log(Level.INFO, "Third "  + isStale(dropdownOption));
        dropdownSelect.selectByVisibleText(option);
        LOGGER.log(Level.INFO, "Fourth "  + isStale(dropdownOption));
    }
}
