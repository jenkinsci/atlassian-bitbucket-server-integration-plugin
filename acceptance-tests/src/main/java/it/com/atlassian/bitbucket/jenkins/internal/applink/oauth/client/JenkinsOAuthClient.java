package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.oauth.OAuth10aService;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class JenkinsOAuthClient {

    private final OAuth10aService oAuthService;

    public JenkinsOAuthClient(String baseUrl, String consumerKey, String consumerSecret) {
        oAuthService = new ServiceBuilder(consumerKey)
                .apiSecret(consumerSecret)
                .build(new JenkinsOAuthApi(baseUrl));
    }

    public OAuth1RequestToken getRequestToken() {
        try {
            return oAuthService.getRequestToken();
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAuthorizationUrl(OAuth1RequestToken requestToken) {
        return oAuthService.getAuthorizationUrl(requestToken);
    }

    public OAuth1AccessToken getAccessToken(OAuth1RequestToken requestToken, String oauthVerifier) {
        try {
            return oAuthService.getAccessToken(requestToken, oauthVerifier);
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
