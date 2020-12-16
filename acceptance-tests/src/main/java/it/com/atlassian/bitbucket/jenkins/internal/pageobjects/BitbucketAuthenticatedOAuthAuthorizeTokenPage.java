package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.openqa.selenium.By;

import java.net.URL;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The page where user authorizes an OAuth request token.
 * Assumes that the use is already authenticated in Bitbucket
 */
public class BitbucketAuthenticatedOAuthAuthorizeTokenPage extends PageObject {

    public static final long TIMEOUT_SECONDS = 5L;

    public BitbucketAuthenticatedOAuthAuthorizeTokenPage(Jenkins jenkins, URL bitbucketAuthorizeUrl) {
        super(jenkins, bitbucketAuthorizeUrl);
    }

    /**
     * Authorizes the request token and waits until the redirect URL has been reached
     *
     * @param redirectUrl
     */
    public void authorize(String redirectUrl) {
        open();
        control(By.cssSelector("span[name=\"authorize\"] > span.first-child > button")).click();
        waitFor().withTimeout(TIMEOUT_SECONDS, SECONDS)
                .withMessage("Redirect URL must contain the 'oauth_verifier' query param")
                .until(layer -> redirectUrl.equals(getCurrentUrl()));
    }
}
