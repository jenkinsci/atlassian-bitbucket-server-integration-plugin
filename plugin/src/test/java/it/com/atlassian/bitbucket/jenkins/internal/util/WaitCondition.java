package it.com.atlassian.bitbucket.jenkins.internal.util;

import java.util.Optional;

public interface WaitCondition {

    /**
     * Run the test condition an return the failure message if the condition did not pass
     *
     * @return The failure message, if the test failed. Or {@link Optional#empty} to indicate that
     *         the test passed
     * @throws Exception
     */
    Optional<String> test() throws Exception;
}
