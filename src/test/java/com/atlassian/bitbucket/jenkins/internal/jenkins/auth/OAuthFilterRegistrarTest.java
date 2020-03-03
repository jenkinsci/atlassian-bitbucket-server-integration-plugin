package com.atlassian.bitbucket.jenkins.internal.jenkins.auth;

import org.junit.Test;

import javax.servlet.ServletException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class OAuthFilterRegistrarTest {

    @Test
    public void addAndRemovedOauthFilter() {
        LocalOAuthFilterRegistrar registrar = new LocalOAuthFilterRegistrar();

        registrar.onStart();
        registrar.onStop();

        assertThat(registrar.filterAddedInvoked, is(true));
        assertThat(registrar.filterRemovedInvoked, is(true));
    }

    @Test
    public void exceptionIsNotThrownWhenUnderlyingThrowsAnException() {
        LocalOAuthFilterRegistrar registrar = new LocalOAuthFilterRegistrar();
        registrar.throwExceptionOnAdd = true;
        registrar.throwExceptionOnRemove = true;

        registrar.onStart();
        registrar.onStop();

        assertThat(registrar.filterAddedInvoked, is(true));
        assertThat(registrar.filterRemovedInvoked, is(true));
    }

    private static class LocalOAuthFilterRegistrar extends OAuthFilterRegistrar {

        private boolean filterAddedInvoked;
        private boolean filterRemovedInvoked;
        private boolean throwExceptionOnAdd;
        private boolean throwExceptionOnRemove;

        @Override
        void addFilter() throws ServletException {
            filterAddedInvoked = true;
            if (throwExceptionOnAdd) {
                throw new ServletException();
            }
        }

        @Override
        void removeFilter() throws ServletException {
            filterRemovedInvoked = true;
            if (throwExceptionOnRemove) {
                throw new ServletException();
            }
        }
    }
}