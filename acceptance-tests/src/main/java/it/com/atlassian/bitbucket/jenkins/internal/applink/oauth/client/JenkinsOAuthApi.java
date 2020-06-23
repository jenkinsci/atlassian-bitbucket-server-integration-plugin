package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client;

import com.github.scribejava.core.builder.api.DefaultApi10a;
import com.github.scribejava.core.services.HMACSha1SignatureService;
import com.github.scribejava.core.services.SignatureService;

import static org.apache.commons.lang3.StringUtils.removeEnd;

public class JenkinsOAuthApi extends DefaultApi10a {

    private final String baseUrl;

    public JenkinsOAuthApi(String baseUrl) {
        this.baseUrl = removeEnd(baseUrl, "/") + "/";
    }

    @Override
    public String getAccessTokenEndpoint() {
        return baseUrl + "bitbucket/oauth/access-token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return baseUrl + "bbs-oauth/authorize";
    }

    @Override
    public String getRequestTokenEndpoint() {
        return baseUrl + "bitbucket/oauth/request-token";
    }

    @Override
    public SignatureService getSignatureService() {
        return new HMACSha1SignatureService();
    }
}
