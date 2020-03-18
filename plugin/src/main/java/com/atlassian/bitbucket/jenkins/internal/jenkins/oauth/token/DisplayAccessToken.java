package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.time.Clock;
import java.util.Date;

public class DisplayAccessToken extends AbstractDescribableImpl<DisplayAccessToken> {

    private Clock clock;
    private ServiceProviderToken token;

    public DisplayAccessToken(ServiceProviderToken token, Clock clock) {
        this.token = token;
        this.clock = clock;
    }

    @SuppressWarnings("unused") // Stapler
    public Date getCreationDate() {
        return new Date(token.getCreationTime());
    }

    public String getConsumerName() {
        return token.getConsumer().getName();
    }

    public String getToken() {
        return token.getToken();
    }

    @SuppressWarnings("unused") // Stapler
    public String getTokenStatus() {
        if (token.hasExpired(clock)) {
            return "EXPIRED";
        } else if (token.hasBeenDenied()) {
            return "DENIED";
        } else if (token.hasBeenAuthorized()) {
            return "AUTHORIZED";
        } else {
            return "NOT YET AUTHORIZED";
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DisplayAccessToken> {

        @Override
        public String getDisplayName() {
            return "Token";
        }
    }
}
