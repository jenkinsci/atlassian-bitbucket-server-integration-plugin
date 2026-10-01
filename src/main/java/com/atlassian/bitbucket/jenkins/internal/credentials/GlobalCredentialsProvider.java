package com.atlassian.bitbucket.jenkins.internal.credentials;

import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Optional;

/**
 * An interface through which one should fetch the global admin credentials. Credential usage is tracked and this should
 * be the only way to fetch the global admin credentials.
 */
public interface GlobalCredentialsProvider {

    /**
     * @return the global admin credentials
     */
    Optional<StringCredentials> getGlobalAdminCredentials();
}
