package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketBranchClient {
    private static final Logger log = Logger.getLogger(BitbucketBranchClient.class.getName());
    private GitClient gitClient;
    private BitbucketSCMRepository repository;
    private String url;

    public BitbucketBranchClient(GitClient gitClient, BitbucketSCMRepository repository) throws InterruptedException {
        this.gitClient = gitClient;
        this.repository = repository;
        this.url = gitClient.getRemoteUrl(repository.getRepositoryName());
    }

    @NonNull
    public Map<String, ObjectId> getRemoteBranches() {
        try {
            return gitClient.getRemoteReferences(url, "*", true, false);
        } catch(Exception exception) {
            log.log(Level.WARNING, exception.getMessage());
        }
        return Collections.emptyMap();
    }

}
