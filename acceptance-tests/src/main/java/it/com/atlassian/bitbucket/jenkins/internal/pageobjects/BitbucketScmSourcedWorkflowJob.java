package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import com.google.inject.Injector;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.WorkflowJob;

import java.net.URL;

/**
 * A {@link WorkflowJob workflow (a.k.a. pipeline) job} that uses a Bitbucket Server SCM to fetch the
 * {@code Jenkinsfile} (as opposed to having the build script saved in Jenkins)
 */
@Describable("org.jenkinsci.plugins.workflow.job.WorkflowJob")
public class BitbucketScmSourcedWorkflowJob extends WorkflowJob {

    public BitbucketScmSourcedWorkflowJob(Injector injector, URL url, String name) {
        super(injector, url, name);
    }

    public BitbucketScm bitbucketScmJenkinsFileSource() {
        select("Pipeline script from SCM");
        select("Bitbucket Server");
        return new BitbucketScm(this, "/definition/scm");
    }

    private void select(final String option) {
        find(by.option(option)).click();
    }
}
