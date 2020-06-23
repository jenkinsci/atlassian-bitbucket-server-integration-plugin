package it.com.atlassian.bitbucket.jenkins.internal.pageobjects;

import hudson.util.VersionNumber;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.Login;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.jenkinsci.test.acceptance.po.User;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import static java.time.Duration.ofSeconds;

/**
 * Page object for Jenkins login page
 * <p>
 * Similar to Jenkins Acceptance Test Harness provided {@link Login login page object}, but includes more explicit waits
 * for elements and conditions with longer timeouts, before and after login, to reduce the chance of being flaky.
 */
public class LoginPage extends PageObject {

    private static final By usernameBy = By.name("j_username");
    private static final By passwordBy = By.name("j_password");
    private static final By submitBy = By.name("Submit");

    /**
     * Time to wait for the login form (elements) to load
     */
    private static final int FORM_LOAD_TIMEOUT_SECONDS = 10;

    /**
     * Time to wait for the login form to disappear after successful login
     */
    private static final int POST_LOGIN_TIMEOUT_SECONDS = 30;

    public LoginPage(Jenkins jenkins) {
        super(jenkins.injector, jenkins.url("login"));
    }

    public LoginForm load() {
        open();
        return new LoginForm();
    }

    public class LoginForm {

        private LoginForm() {
        }

        public LoginResult login(String username, String password) {
            waitFor(usernameBy, FORM_LOAD_TIMEOUT_SECONDS).sendKeys(username);
            waitFor(passwordBy, FORM_LOAD_TIMEOUT_SECONDS).sendKeys(password);
            submitAndWaitUntilStale(waitFor(submitBy, FORM_LOAD_TIMEOUT_SECONDS));
            return new LoginResult(username);
        }

        public LoginResult login(String user) {
            return login(user, user);
        }

        public LoginResult login(User user) {
            return login(user.fullName(), user.fullName());
        }

        private void submitAndWaitUntilStale(WebElement submit) {
            submit.submit();
            waitFor(submit)
                    .withTimeout(ofSeconds(POST_LOGIN_TIMEOUT_SECONDS))
                    // There's an issue with inner classes in Java where if one accesses a protected static method of
                    // its outer class's parent (in this case CapybaraPortingLayerImpl's 'isStale' method), it results
                    // in an IllegalAccessError. Do not replace this lambda with a static method reference!
                    .until(element -> isStale(element));
        }
    }

    public class LoginResult {

        private final String user;

        private LoginResult(String user) {
            this.user = user;
        }

        public boolean isSuccessfulLogin() {
            return isLoggedInAs(user);
        }

        public boolean isFailedLogin() {
            String invalidLoginMessage = getJenkins().getVersion().isOlderThan(new VersionNumber("2.128"))
                    ? "Invalid login information. Please try again."
                    : "Invalid username or password";
            try {
                waitFor(by.xpath("//div[contains(text(), '%s')]", invalidLoginMessage), POST_LOGIN_TIMEOUT_SECONDS);
                return true;
            } catch (NoSuchElementException e) {
                return false;
            }
        }

        public boolean isLoggedInAs(User user) {
            return isLoggedInAs(user.fullName());
        }

        public boolean isLoggedInAs(String user) {
            try {
                waitFor(by.href("/user/" + user), POST_LOGIN_TIMEOUT_SECONDS);
                return true;
            } catch (NoSuchElementException e) {
                return false;
            }
        }
    }

    public static Matcher<LoginResult> isSuccessfulLogin() {
        return new LoginResultMatcher(true);
    }

    public static Matcher<LoginResult> isFailedLogin() {
        return new LoginResultMatcher(false);
    }

    private static final class LoginResultMatcher extends TypeSafeDiagnosingMatcher<LoginResult> {

        private final boolean expectsSuccessfulLogin;

        private LoginResultMatcher(boolean expectsSuccessfulLogin) {
            this.expectsSuccessfulLogin = expectsSuccessfulLogin;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(String.format(" is %s login", expectsSuccessfulLogin ? "successful" : "failed"));
        }

        @Override
        protected boolean matchesSafely(LoginResult result, Description mismatchDescription) {
            if (expectsSuccessfulLogin != result.isSuccessfulLogin()) {
                mismatchDescription.appendText(
                        String.format(" is %s login", expectsSuccessfulLogin ? "successful" : "failed"));
                return false;
            }
            return true;
        }
    }
}
