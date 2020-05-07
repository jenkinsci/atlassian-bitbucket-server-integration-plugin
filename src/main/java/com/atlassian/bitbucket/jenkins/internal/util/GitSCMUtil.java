package com.atlassian.bitbucket.jenkins.internal.util;

import hudson.plugins.git.GitSCM;
import org.eclipse.jgit.transport.RemoteConfig;

public class GitSCMUtil {

    private GitSCMUtil() { }

    /**
     * Extracts a remote config representation of the BitbucketRepository from a GitSCM
     * @param gitSCM a GitSCM attached to a BitbucketSCM
     * @return the SCM representation of the repository
     */
    public static RemoteConfig getGitRepository(GitSCM gitSCM) {
        return gitSCM.getRepositories().get(0);
    }
}
