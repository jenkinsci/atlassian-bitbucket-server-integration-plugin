package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.plugins.credentials.BaseStandardCredentials;
import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.po.*;

/**
 * Represents the {@link PageAreaImpl page area} used for adding a new Bitbucket Server access token credentials to
 * Jenkins
 *
 * @see CredentialsPage#add(Class)
 */
@Describable("Bitbucket personal access token")
public class BitbucketTokenCredentials extends BaseStandardCredentials {

    public final Control token = control(by.name("_.secret"));

    public BitbucketTokenCredentials(PageObject context, String path) {
        super(context, path);
    }

    public BitbucketTokenCredentials(PageArea area, String relativePath) {
        super(area, relativePath);
    }
}
