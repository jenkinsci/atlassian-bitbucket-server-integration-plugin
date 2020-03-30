package com.atlassian.bitbucket.jenkins.internal.scm;

public enum CloneProtocol {
    HTTP("http"),
    SSH("ssh");

    public final String name;

    private CloneProtocol(String name) {
        this.name = name;
    }
}
