package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider;

import net.oauth.OAuth;
import net.oauth.server.HttpRequestMessage;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.AccessTokenRestEndpoint.ACCESS_TOKEN_PATH_END;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.RequestTokenRestEndpoint.REQUEST_TOKEN_PATH_END;
import static net.oauth.OAuth.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Utility class for extracting information from OAuth requests.
 */
public class OAuthRequestUtils {

    public static final String EXCLUSION_PATH = "/bitbucket/oauth/";
    private static final Set<String> OAUTH_DATA_REQUEST_PARAMS = Stream.of(OAUTH_CONSUMER_KEY,
            OAUTH_TOKEN,
            OAUTH_SIGNATURE_METHOD,
            OAUTH_SIGNATURE,
            OAUTH_TIMESTAMP,
            OAUTH_NONCE).collect(Collectors.toSet());

    /**
     * Checks if the request is any form of OAuth request, either 2LO or 3LO.
     * It is done by checking the request parameters.
     *
     * @param request the request object.
     * @return true if the request is an OAuth request.
     */
    public boolean isOAuthAccessAttempt(HttpServletRequest request) {
        return is3LOAuthAccessAttempt(request) || is2LOAuthAccessAttempt(request);
    }

    /**
     * The request path is accessing one of the token endpoints
     *
     * @param request the request object
     * @return true if the request is for an OAuth token.
     */
    public boolean isOauthTokenRequest(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return !isEmpty(pathInfo) && pathInfo.startsWith(OAuthRequestUtils.EXCLUSION_PATH);
    }

    /**
     * This is 2LO trying to access an OAuth protected resource if all the 2LO parameters are set while 3LO specific
     * parameters are not present.
     *
     * @param request the request object.
     * @return true if the request is an 2LO request.
     */
    private static boolean is2LOAuthAccessAttempt(HttpServletRequest request) {
        Map<String, String> params = extractParameters(request);

        // http://oauth.googlecode.com/svn/spec/ext/consumer_request/1.0/drafts/2/spec.html
        // oauth_token: MUST be included with an empty value to indicate this is a two-legged request
        return params.keySet().containsAll(OAUTH_DATA_REQUEST_PARAMS) &&
               isEmpty(params.get(OAUTH_TOKEN)) &&
               !isRequestTokenRequest(request);
    }

    /**
     * This is 3LO trying to access an OAuth protected resource if all the 3LO parameters are set and we aren't trying to
     * turn a request token into an access token (which is the only other time all the OAuth parameters are in the
     * request).
     *
     * @param request the request object.
     * @return true if the request is an 3LO request.
     */
    private static boolean is3LOAuthAccessAttempt(HttpServletRequest request) {
        Map<String, String> params = extractParameters(request);

        // all the oauth request parameters must be present and oauth_token must not be empty
        return params.keySet().containsAll(OAUTH_DATA_REQUEST_PARAMS) &&
               params.containsKey(OAUTH_TOKEN) &&
               !isEmpty(params.get(OAUTH_TOKEN)) &&
               !isAccessTokenRequest(request);
    }

    /**
     * Checks if this request is a request token request.
     *
     * @param request the request object.
     * @return true if it's a request token request.
     */
    private static boolean isRequestTokenRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith(REQUEST_TOKEN_PATH_END);
    }

    /**
     * Checks if this request is an access token request.
     *
     * @param request the request object.
     * @return true if it's an access token request.
     */
    private static boolean isAccessTokenRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith(ACCESS_TOKEN_PATH_END);
    }

    /**
     * @return available parameters in the request.
     */
    private static Map<String, String> extractParameters(HttpServletRequest request) {
        return HttpRequestMessage.getParameters(request).stream()
                .collect(Collectors.toMap(OAuth.Parameter::getKey, OAuth.Parameter::getValue, (a, b) -> a)); //if there are duplcates use the first one found
    }
}