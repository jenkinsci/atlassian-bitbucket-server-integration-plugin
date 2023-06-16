package it.com.atlassian.bitbucket.jenkins.internal.util;

import org.openqa.selenium.StaleElementReferenceException;

public class BrowserUtils {

    private static final long RETRY_TIMEOUT_MILLISECONDS = 5000L;

    public static final void runWithRetry(RetryableAction action) {
        long startTime = System.currentTimeMillis();
        boolean retry = true;

        while (retry) {
            try {
                action.execute();
                retry = false;
            } catch (StaleElementReferenceException e) {
                if (System.currentTimeMillis() - startTime > RETRY_TIMEOUT_MILLISECONDS) {
                    throw new RetryFailedException("Action failed after " + RETRY_TIMEOUT_MILLISECONDS + "ms", e);
                }
            }
        }
    }

    @FunctionalInterface
    public interface RetryableAction {

        void execute();
    }

    public static class RetryFailedException extends RuntimeException {

        public RetryFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
