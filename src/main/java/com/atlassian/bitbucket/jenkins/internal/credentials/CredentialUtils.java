package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nullable;
import java.util.*;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public final class CredentialUtils {

    private static final List<Class> CREDENTIAL_TYPES = Arrays.asList(StringCredentials.class,
            UsernamePasswordCredentials.class, BasicSSHUserPrivateKey.class);

    private CredentialUtils() {
        throw new UnsupportedOperationException(
                CredentialUtils.class.getName() + " should not be instantiated");
    }

    public static Optional<Credentials> getCredentials(@Nullable String credentialsId) {
        return CREDENTIAL_TYPES.stream().map(type -> firstOrNull(
                lookupCredentials(type, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()),
                withId(trimToEmpty(credentialsId))))
                .filter(Objects::nonNull)
                .findAny();
    }
}
