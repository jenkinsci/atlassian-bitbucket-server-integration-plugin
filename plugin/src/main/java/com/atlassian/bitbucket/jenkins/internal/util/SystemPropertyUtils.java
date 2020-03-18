package com.atlassian.bitbucket.jenkins.internal.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SystemPropertyUtils {

    private static final Logger LOG = Logger.getLogger(SystemPropertyUtils.class.getName());

    /**
     * @param propertyName a JVM system property
     * @param defaultValue the value to return if the propertyName is not defined or not a valid String representation
     *                     of a long
     * @return the parsed long value of the system property value, or the defaultValue if the system property is
     *         undefined or invalid
     */
    public static long parsePositiveLongFromSystemProperty(String propertyName, long defaultValue) {
        final String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            try {
                final long longValue = Long.parseLong(propertyValue);
                if (longValue >= 0) {
                    return longValue;
                } else {
                    LOG.log(Level.WARNING, String.format("Value of system property '%s' is negative ('%s') defaulting to %s",
                            propertyName, longValue, defaultValue));
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, String.format(
                        "Failed to parse long value from system property '%s' (was: '%s'), defaulting to %s",
                        propertyName, propertyValue, defaultValue), e);
            }
        }
        return defaultValue;
    }
}
