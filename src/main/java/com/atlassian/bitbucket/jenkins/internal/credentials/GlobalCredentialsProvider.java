package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;

import java.util.Optional;

/**
 * An interface through which one should fetch admin and global credentials. Credential usage is tracked
 * and this should be the only way to fetch credentials.
 */
public interface GlobalCredentialsProvider {

    Optional<BitbucketTokenCredentials> getGlobalAdminCredentials();

    Optional<Credentials> getGlobalCredentials();
}
