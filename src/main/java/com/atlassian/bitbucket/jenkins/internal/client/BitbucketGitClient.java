package com.atlassian.bitbucket.jenkins.internal.client;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.Branch;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketGitClient {
    private static final Logger log = Logger.getLogger(BitbucketGitClient.class.getName());
    private GitClient gitClient;

    public BitbucketGitClient(GitClient gitClient) {
        if(gitClient != null) {
            this.gitClient = gitClient;
        }
    }

    @NonNull
    public Set<Branch> getRemoteBranches() {
        try {
            return gitClient.getRemoteBranches();
        } catch(Exception e) {
            log.log(Level.WARNING, e.getMessage());
        }
        return Collections.emptySet();
    }

}
