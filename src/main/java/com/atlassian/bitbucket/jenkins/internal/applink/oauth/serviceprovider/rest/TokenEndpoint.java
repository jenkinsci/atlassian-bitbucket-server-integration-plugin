package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest;

import hudson.model.InvisibleAction;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.io.IOException;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.AccessTokenRestEndpoint.ACCESS_TOKEN_PATH_END;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.rest.RequestTokenRestEndpoint.REQUEST_TOKEN_PATH_END;

public class TokenEndpoint extends InvisibleAction {

    private AccessTokenRestEndpoint accessTokenRestEndpoint;
    private RequestTokenRestEndpoint requestTokenRestEndpoint;

    @Inject
    public TokenEndpoint(
            AccessTokenRestEndpoint accessTokenRestEndpoint,
            RequestTokenRestEndpoint requestTokenRestEndpoint) {
        this.accessTokenRestEndpoint = accessTokenRestEndpoint;
        this.requestTokenRestEndpoint = requestTokenRestEndpoint;
    }

    @RequirePOST
    @WebMethod(name = ACCESS_TOKEN_PATH_END)
    public void doAccessToken(StaplerRequest request,
                              StaplerResponse response) throws ServletException, IOException {
        accessTokenRestEndpoint.handleAccessToken(request, response);
    }

    @RequirePOST
    @WebMethod(name = REQUEST_TOKEN_PATH_END)
    public void doRequestToken(StaplerRequest req,
                               StaplerResponse resp) throws ServletException, IOException {
        requestTokenRestEndpoint.handleRequestToken(req, resp);
    }
}
