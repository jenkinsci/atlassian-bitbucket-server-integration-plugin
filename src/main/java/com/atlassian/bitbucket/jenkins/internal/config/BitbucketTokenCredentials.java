package com.atlassian.bitbucket.jenkins.internal.config;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

@NameWith(BitbucketTokenCredentials.NameProvider.class)
public interface BitbucketTokenCredentials extends StringCredentials {

    class NameProvider extends CredentialsNameProvider<BitbucketTokenCredentialsImpl> {

        @Override
        public String getName(BitbucketTokenCredentialsImpl bitbucketTokenCredentials) {
            return bitbucketTokenCredentials.getDescription() + " - Bitbucket admin token";
        }
    }
}
