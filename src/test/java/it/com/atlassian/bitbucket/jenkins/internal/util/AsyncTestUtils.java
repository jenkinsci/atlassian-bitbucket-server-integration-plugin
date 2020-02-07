package it.com.atlassian.bitbucket.jenkins.internal.util;

import java.util.Optional;

public final class AsyncTestUtils {

    private AsyncTestUtils() {
        // utility class
    }

    /**
     * Waits for the {@link WaitCondition#test()} to return true, testing every 50ms. Throws a
     * {@link WaitConditionFailure} if {@code waitCondition} does not become true within the {@code
     * timeoutMs} period.
     *
     * @param waitCondition the condition
     * @param timeoutMs the maximum time in millis to wait for the condition to become true
     * @throws WaitConditionFailure if the {@link WaitCondition} is not met by the provided {@code
     *         timeoutMs}
     */
    public static void waitFor(WaitCondition waitCondition, long timeoutMs) {
        waitFor(waitCondition, timeoutMs, 50);
    }

    /**
     * Waits for the {@link WaitCondition#test()} to return true, testing every {@code
     * retryIntervalMs}. Throws a {@link WaitConditionFailure} if {@code waitCondition} does not
     * become true within the {@code timeoutMs} period.
     *
     * @param waitCondition the condition
     * @param timeoutMs the maximum time in millis to wait for the condition to become true
     * @param retryIntervalMs the time in millis between tests
     * @throws WaitConditionFailure if the {@link WaitCondition} is not met by the provided {@code
     *         timeoutMs}
     */
    public static void waitFor(WaitCondition waitCondition, long timeoutMs, long retryIntervalMs) {
        Optional<String> failureMessage;
        try {
            long startTime = System.currentTimeMillis();
            failureMessage = waitCondition.test();
            while (failureMessage.isPresent() && System.currentTimeMillis() - startTime < timeoutMs) {
                Thread.sleep(retryIntervalMs);
                failureMessage = waitCondition.test();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (failureMessage.isPresent()) {
            throw new WaitConditionFailure(failureMessage.get());
        }
    }
}
