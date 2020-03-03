package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth.TrustedUnderlyingSystemAuthorizerFilter;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.NoSuchUserException;
import hudson.model.User;
import hudson.security.ACLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.USER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Before
    public void setup() {
        when(user.getFullName()).thenReturn(USER);
    }

    @Test
    public void successfulLogin() throws IOException, ServletException {
        TrustedUnderlyingSystemAuthorizerFilter filter = LocalTrustedJenkinsAuthorizer.createInstance(user, aclContext);
        filter.authorize(USER, request, response, chain);

        verify(chain).doFilter(request, response);
        verify(aclContext).close();
    }

    @Test(expected = NoSuchUserException.class)
    public void throwsExceptionForUnknownUsers() throws IOException, ServletException {
        TrustedUnderlyingSystemAuthorizerFilter filter = LocalTrustedJenkinsAuthorizer.createInstance(user, aclContext);
        filter.authorize("gaurav", request, response, chain);
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
        User getUser(String userName) {
            if (userName.equals(u.getFullName())) {
                return u;
            }
            return null;
        }

        @Override
        ACLContext createACLContext(User u) {
            assert this.u == u;
            return aclContext;
        }
    }
}