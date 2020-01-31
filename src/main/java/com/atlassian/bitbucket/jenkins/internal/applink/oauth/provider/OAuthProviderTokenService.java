package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.OAuthToken;
import com.google.inject.ImplementedBy;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import org.acegisecurity.Authentication;

@ImplementedBy(OAuthProviderTokenServiceImpl.class)
public interface OAuthProviderTokenService {

    /**
     * Authorize a request token
     *
     * @param requestToken the request token to be authorized
     * @return an OAuth verification code
     * @throws OAuthException if authorization of the request token fails
     */
    OAuthToken authorizeRequestToken(String requestToken, Authentication authentication) throws OAuthException;

    /**
     * Generate an access token given a request token
     *
     * @param requestToken the request token to verify before generating an access token
     * @return a new access token
     * @throws OAuthException if the given request token cannot be verified
     */
    OAuthToken generateAccessToken(String requestToken) throws OAuthException;

    /**
     * Generate a request token
     *
     * @param consumer    the consumer to generate the request token for
     * @param callbackUrl the callbackUrl URL associated with the request token
     * @param message     the {@link OAuthMessage OAuth message} associated with the request token
     * @return a new request token
     */
    OAuthToken generateRequestToken(OAuthConsumer consumer, String callbackUrl, OAuthMessage message);
}
