package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.cloudbees.plugins.credentials.Credentials;

import java.util.Optional;

public interface GlobalCredentialsProvider {

    Optional<Credentials> getGlobalAdminCredentials();

    Optional<Credentials> getGlobalCredentials();
}
