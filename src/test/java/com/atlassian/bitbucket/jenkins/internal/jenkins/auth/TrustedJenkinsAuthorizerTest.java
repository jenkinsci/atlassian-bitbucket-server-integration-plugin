package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.TrustedUnderlyingSystemAuthorizerFilter;
import hudson.model.User;
import hudson.security.ACLContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TrustedJenkinsAuthorizerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private User user;
    @Mock
    private ACLContext aclContext;

    @Test
    public void successfulLogin() throws IOException, ServletException {
        TrustedUnderlyingSystemAuthorizerFilter filter = LocalTrustedJenkinsAuthorizer.createInstance(user, aclContext);
        filter.authorize(user, request, response, chain);

        verify(chain).doFilter(request, response);
        verify(aclContext).close();
    }

    private static class LocalTrustedJenkinsAuthorizer extends TrustedJenkinsAuthorizer {

        private final User u;
        private final ACLContext aclContext;

        private LocalTrustedJenkinsAuthorizer(User u, ACLContext aclContext) {
            this.u = u;
            this.aclContext = aclContext;
        }

        static TrustedJenkinsAuthorizer createInstance(User u, ACLContext context) {
            return new LocalTrustedJenkinsAuthorizer(u, context);
        }

        @Override
        ACLContext createACLContext(User u) {
            assert this.u == u;
            return aclContext;
        }
    }
}