package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.OAuthProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.OAuthToken;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import org.acegisecurity.Authentication;

import javax.inject.Singleton;
import java.security.SecureRandom;
import java.util.Random;
import java.util.logging.Logger;

import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

/**
 * Implementation of {@link OAuthProviderTokenService} that uses delegates storage of generated tokens to
 * {@link OAuthProviderTokenStore} and generation of a {@code verifier} to {@link OAuthTokenVerifierGenerator}.
 * <br>
 * The random token generation is based on the
 * <a href="https://github.com/spring-projects/spring-security-oauth/blob/master/spring-security-oauth/src/main/java/org/springframework/security/oauth/provider/token/RandomValueProviderTokenServices.java">Spring OAuth Provider Library</a>
 */
@Singleton
public class OAuthProviderTokenServiceImpl implements OAuthProviderTokenService {

    private static final int TOKEN_SECRET_LENGTH_BYTES = 80;

    private static final Logger log = Logger.getLogger(OAuthProviderTokenServiceImpl.class.getName());

    private final Random random;
    private final OAuthProviderTokenStore tokenStore;
    private final OAuthTokenVerifierGenerator verifierService;

    public OAuthProviderTokenServiceImpl(OAuthProviderTokenStore tokenStore,
                                         OAuthTokenVerifierGenerator verifierService) {
        this.tokenStore = tokenStore;
        this.verifierService = verifierService;

        random = new SecureRandom();
    }

    @Override
    public OAuthToken authorizeRequestToken(String requestToken, Authentication authentication) throws OAuthException {
        requireNonNull(authentication, "authentication");

        if (!authentication.isAuthenticated() || "anonymous".equalsIgnoreCase(authentication.getName())) {
            log.warning("User must be authenticated to authorize a token");
            throw new OAuthException("Must be authenticated to authorize a token");
        }

        OAuthToken token = tokenStore.get(requireNonNull(requestToken, "requestToken"))
                .orElseThrow(() -> {
                    log.warning("Request token not found: " + requestToken);
                    return new OAuthException("Request token not found");
                });

        if (token.isAccessToken()) {
            log.warning("Expected a request token but found an access token instead: " + token.getTokenValue());
            throw new OAuthException("Expected a request token but found an access token instead");
        }

        token.setVerifier(verifierService.generateVerifier());
        token.setAuthorizedBy(authentication.getName());

        tokenStore.put(token);

        return token;
    }

    @Override
    public OAuthToken generateAccessToken(String requestToken) throws OAuthException {
        requireNonNull(requestToken, "requestToken");

        OAuthToken token = tokenStore.get(requestToken)
                .orElseThrow(() -> {
                    log.warning("Request token not found: " + requestToken);
                    return new OAuthException("Request token not found");
                });

        if (token.isAccessToken()) {
            log.warning("Token is not a request token: " + requestToken);
            throw new OAuthException("Token is not a request token");
        }

        if (token.getVerifier() == null || token.getAuthorizedBy() == null) {
            log.warning(() -> String.format("Token is not verified (verifier=%s, authorizedBy=%s)",
                    token.getVerifier(), token.getAuthorizedBy()));
            throw new OAuthException("Token is not verified");
        }

        tokenStore.remove(requestToken);

        log.fine("Request token was used to generate an access token: " + requestToken);

        OAuthToken accessToken = new OAuthToken(true, token.getCallbackUrl(), token.getConsumerKey(),
                generateTokenSecret(), currentTimeMillis(), randomUUID().toString());

        tokenStore.put(accessToken);

        return accessToken;
    }

    @Override
    public OAuthToken generateRequestToken(OAuthConsumer consumer, String callbackUrl, OAuthMessage message) {
        requireNonNull(consumer, "consumer");
        OAuthToken token = new OAuthToken(false, callbackUrl, consumer.consumerKey, generateTokenSecret(),
                currentTimeMillis(), randomUUID().toString());
        tokenStore.put(token);
        return token;
    }

    private String generateTokenSecret() {
        byte[] secretBytes = new byte[TOKEN_SECRET_LENGTH_BYTES];
        random.nextBytes(secretBytes);
        return new String(encodeBase64(secretBytes), UTF_8);
    }
}
