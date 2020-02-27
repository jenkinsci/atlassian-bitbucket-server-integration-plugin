package com.atlassian.bitbucket.jenkins.internal.scm;

public enum CloneProtocol {
    HTTP("http"),
    SSH("ssh");

    public final String filter;

    private CloneProtocol(String filter) {
        this.filter = filter;
    }
}
