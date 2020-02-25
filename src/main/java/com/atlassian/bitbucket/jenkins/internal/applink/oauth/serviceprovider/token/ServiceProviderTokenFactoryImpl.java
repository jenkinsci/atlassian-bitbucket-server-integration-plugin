package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.Randomizer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newAccessToken;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newRequestToken;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.logging.Level.FINE;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Implementation of {@link ServiceProviderTokenFactory} that uses {@link Randomizer randomly generated values} to
 * generate request and access tokens.
 */
@Singleton
public class ServiceProviderTokenFactoryImpl implements ServiceProviderTokenFactory {

    private static final int ACCESS_TOKEN_SESSION_LENGTH_BYTES = 80;
    private static final int TOKEN_SECRET_LENGTH_BYTES = 80;

    private static final Logger log = Logger.getLogger(ServiceProviderTokenFactoryImpl.class.getName());

    private final Randomizer randomizer;

    @Inject
    public ServiceProviderTokenFactoryImpl(Randomizer randomizer) {
        this.randomizer = randomizer;
    }

    @Override
    public ServiceProviderToken generateAccessToken(ServiceProviderToken requestToken) {
        requireNonNull(requestToken, "requestToken");

        if (requestToken.isAccessToken()) {
            log.warning("Token is not a request token: " + requestToken);
            throw new InvalidTokenException("Token is not a request token");
        }

        if (requestToken.getUser() == null || isBlank(requestToken.getVerifier())) {
            log.warning("Request token is not authorized: " + requestToken);
            throw new InvalidTokenException("Request token is not authorized");
        }

        if (log.isLoggable(FINE)) {
            log.fine(String.format("Request token '%s' was used to generate an access token", requestToken));
        }

        return newAccessToken(randomUUID().toString())
                .callback(requestToken.getCallback())
                .consumer(requestToken.getConsumer())
                .creationTime(currentTimeMillis())
                .tokenSecret(randomizer.randomUrlSafeString(TOKEN_SECRET_LENGTH_BYTES))
                .authorizedBy(requestToken.getUser())
                .verifier(requestToken.getVerifier())
                .session(newSession(requestToken))
                .build();
    }

    @Override
    public ServiceProviderToken generateRequestToken(Consumer consumer) {
        requireNonNull(consumer, "consumer");
        return newRequestToken(randomUUID().toString())
                .consumer(consumer)
                .creationTime(currentTimeMillis())
                .tokenSecret(randomizer.randomUrlSafeString(TOKEN_SECRET_LENGTH_BYTES))
                .build();
    }

    @Override
    public ServiceProviderToken generateRequestToken(Consumer consumer, URI callback) {
        requireNonNull(consumer, "consumer");
        requireNonNull(callback, "callback");
        return newRequestToken(randomUUID().toString())
                .callback(callback)
                .consumer(consumer)
                .creationTime(currentTimeMillis())
                .tokenSecret(randomizer.randomUrlSafeString(TOKEN_SECRET_LENGTH_BYTES))
                .build();
    }

    private ServiceProviderToken.Session newSession(ServiceProviderToken token) {
        ServiceProviderToken.Session.Builder builder =
                ServiceProviderToken.Session.newSession(randomizer.randomUrlSafeString(ACCESS_TOKEN_SESSION_LENGTH_BYTES));
        if (token.getSession() != null) {
            builder.creationTime(token.getSession().getCreationTime());
        }
        return builder.build();
    }
}
