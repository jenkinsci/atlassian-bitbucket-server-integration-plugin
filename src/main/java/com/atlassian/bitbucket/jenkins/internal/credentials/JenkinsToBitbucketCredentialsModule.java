package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class JenkinsToBitbucketCredentialsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JenkinsToBitbucketCredentials.class)
                .to(JenkinsToBitbucketCredentialsImpl.class)
                .in(Singleton.class);
    }
}
