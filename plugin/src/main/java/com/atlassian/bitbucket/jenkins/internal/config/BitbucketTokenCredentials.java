package com.atlassian.bitbucket.jenkins.internal.config;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.util.Secret;

@NameWith(BitbucketTokenCredentials.NameProvider.class)
public interface BitbucketTokenCredentials extends StandardCredentials {

    Secret getSecret();

    class NameProvider extends CredentialsNameProvider<BitbucketTokenCredentialsImpl> {

        @Override
        public String getName(BitbucketTokenCredentialsImpl bitbucketTokenCredentials) {
            return bitbucketTokenCredentials.getDescription() + " - Bitbucket admin token";
        }
    }
}
