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

@Singleton
public class OAuthProviderTokenServiceImpl implements OAuthProviderTokenService {

    private static final Logger log = Logger.getLogger(OAuthProviderTokenServiceImpl.class.getName());

    private final Random random;
    private final OAuthProviderTokenStore tokenStore;
    private final OAuthProviderVerifierService verifierService;

    private int tokenSecretLengthBytes = 80;

    public OAuthProviderTokenServiceImpl(OAuthProviderTokenStore tokenStore,
                                         OAuthProviderVerifierService verifierService) {
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
            log.warning("Requested to authorize an access token: " + token.getTokenValue());
            throw new OAuthException("Requested to authorize an access token");
        }

        token.setVerifier(verifierService.generateVerifier());
        token.setAuthorizedBy(authentication.getName());
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

        boolean deleted = tokenStore.remove(requestToken);
        if (deleted) {
            log.fine("Request token was used to generate an access token: " + requestToken);
        }

        byte[] secretBytes = new byte[tokenSecretLengthBytes];
        random.nextBytes(secretBytes);
        String secret = new String(encodeBase64(secretBytes), UTF_8);
        String tokenValue = randomUUID().toString();
        OAuthToken accessToken = new OAuthToken(true, token.getCallbackUrl(), token.getConsumerKey(), secret,
                currentTimeMillis(), tokenValue);
        tokenStore.put(accessToken);
        return accessToken;
    }

    @Override
    public OAuthToken generateRequestToken(OAuthConsumer consumer, String callbackUrl, OAuthMessage message) {
        requireNonNull(consumer, "consumer");
        byte[] secretBytes = new byte[tokenSecretLengthBytes];
        random.nextBytes(secretBytes);
        String secret = new String(encodeBase64(secretBytes), UTF_8);
        String tokenValue = randomUUID().toString();
        OAuthToken token =
                new OAuthToken(false, callbackUrl, consumer.consumerKey, secret, currentTimeMillis(), tokenValue);
        tokenStore.put(token);
        return token;
    }
}
