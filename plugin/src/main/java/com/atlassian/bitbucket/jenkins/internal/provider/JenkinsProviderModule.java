package com.atlassian.bitbucket.jenkins.internal.provider;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class JenkinsProviderModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JenkinsProvider.class)
                .to(DefaultJenkinsProvider.class)
                .in(Singleton.class);
    }
}
