package it.com.atlassian.bitbucket.jenkins.internal.util;

public class WaitConditionFailure extends RuntimeException {

    public WaitConditionFailure(String message) {
        super(message);
    }
}
