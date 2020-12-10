package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jenkinsci.test.acceptance.junit.Wait;
import org.jenkinsci.test.acceptance.po.CapybaraPortingLayer;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.net.URI;
import java.net.URL;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;

/**
 * The page where user authorizes an OAuth request token.
 */
public class OAuthAuthorizeTokenPage extends PageObject {

    public static final long TIMEOUT_SECONDS = 5L;

    private final String requestToken;

    public OAuthAuthorizeTokenPage(Jenkins context, URL pageUrl, String requestTokenValue) {
        super(context, pageUrl);
        this.requestToken = requestTokenValue;
    }

    /**
     * Authorizes the request token and returns the {@code oauth_verifier} query parameter from the redirect URL
     *
     * @return the {@code oauth_verifier} query parameter from the redirect URL after authorizing the token
     */
    public String authorize() {
        openAndVerify();
        control(By.cssSelector("span[name=\"authorize\"] > span.first-child > button")).click();
        return waitWithTimeout()
                .withMessage("Redirect URL must contain the 'oauth_verifier' query param")
                .until(this::untilOAuthVerifierQueryParamIsPresent);
    }

    /**
     * Cancels (denies) the authorization and checks that the {@code oauth_verifier} query parameter in the redirect URL
     * has the value {@code 'denied'}
     */
    public void cancel() {
        openAndVerify();
        control(By.cssSelector("span[name=\"cancel\"] > span.first-child > button")).click();
        String oAuthVerifier = waitWithTimeout()
                .withMessage("Redirect URL must contain the 'oauth_verifier' query param")
                .until(this::untilOAuthVerifierQueryParamIsPresent);
        assertThat(oAuthVerifier, equalToIgnoringWhiteSpace("denied"));
    }

    private void openAndVerify() {
        open();
        String requestTokenValue = waitWithTimeout()
                .withMessage("Authorize page must contain a hidden input holding the request token value")
                .until(() -> {
                    WebElement oauthToken =
                            driver.findElement(By.cssSelector("input.setting-input[name=\"oauth_token\"]"));
                    return ofNullable(oauthToken).map(ot -> ot.getAttribute("value")).orElse(null);
                });
        assertThat("Request token to be authorized has the wrong value", requestTokenValue,
                equalToIgnoringWhiteSpace(requestToken));
    }

    private String untilOAuthVerifierQueryParamIsPresent() {
        return URLEncodedUtils.parse(URI.create(getCurrentUrl()), UTF_8).stream()
                .filter(nvp -> "oauth_verifier".equalsIgnoreCase(nvp.getName()))
                .findFirst()
                .map(NameValuePair::getValue)
                .orElseThrow(() ->
                        new AssertionError("Redirect url must contain an 'oauth_verifier' query parameter"));
    }

    private Wait<CapybaraPortingLayer> waitWithTimeout() {
        return waitFor().withTimeout(TIMEOUT_SECONDS, SECONDS);
    }
}
