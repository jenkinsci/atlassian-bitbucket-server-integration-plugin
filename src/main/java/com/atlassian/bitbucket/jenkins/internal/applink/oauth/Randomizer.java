package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.google.inject.ImplementedBy;

/**
 * Generates random values.
 */
@ImplementedBy(RandomizerImpl.class)
public interface Randomizer {

    /**
     * Generates and returns a random alphanumeric string of the specified length.
     *
     * @param length length of the random string (in chars)
     * @return random alphanumeric string of the specified length
     */
    String randomAlphanumericString(int length);

    /**
     * Generates a URL-safe random string with the given length
     * <br>
     * The resulting string will contain ASCII characters [0-9], [A-Z], and [a-z], as well as the URL-safe characters
     * '-' and '_', and can be safely used anywhere in the URL, including as query parameters
     *
     * @param length length of the random string (in chars)
     * @return random URL-safe string of the specified length
     */
    String randomUrlSafeString(int length);
}
