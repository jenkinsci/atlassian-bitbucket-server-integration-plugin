package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.TrustedUnderlyingSystemAuthorizerFilter;
import com.atlassian.bitbucket.jenkins.internal.jenkins.auth.TrustedJenkinsAuthorizer;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import hudson.Extension;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;

import java.time.Clock;

/**
 * All guice wiring for 3rd party library for OAuth module should go here.
 */
@Extension
public class OAuthModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(OAuthValidator.class).to(SimpleOAuthValidator.class).in(Singleton.class);
        bind(TrustedUnderlyingSystemAuthorizerFilter.class).to(TrustedJenkinsAuthorizer.class).in(Singleton.class);
    }
}
