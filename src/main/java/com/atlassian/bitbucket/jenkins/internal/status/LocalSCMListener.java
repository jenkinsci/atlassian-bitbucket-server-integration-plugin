package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepositoryHelper;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.jenkinsci.plugins.workflow.libs.*;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Extension
public class LocalSCMListener extends SCMListener {

    private BuildStatusPoster buildStatusPoster;
    private BitbucketSCMRepositoryHelper repositoryHelper;

    public LocalSCMListener() {
    }

    @Inject
    LocalSCMListener(BuildStatusPoster buildStatusPoster, BitbucketSCMRepositoryHelper repositoryHelper) {
        this.buildStatusPoster = buildStatusPoster;
        this.repositoryHelper = repositoryHelper;
    }

    @CheckForNull
    public GitSCM getUnderlyingGitSCM(SCM scm) {
        if (scm instanceof GitSCM) {
            // Already a git SCM
            return (GitSCM) scm;
        }
        if (scm instanceof BitbucketSCM) {
            BitbucketSCM bitbucketSCM = (BitbucketSCM) scm;
            if (bitbucketSCM.getServerId() != null) {
                return bitbucketSCM.getGitSCM();
            }
        }
        return null;
    }

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           @CheckForNull File changelogFile,
                           @CheckForNull SCMRevisionState pollingBaseline) {

        // Check if the current SCM we are checking out is configured as a library SCM. We don't want to send status
        // to library SCMs.
        for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
            for (LibraryConfiguration cfg : resolver.forJob(build.getParent(), Collections.emptyMap())) {
                if (cfg.getRetriever() instanceof SCMRetriever) {
                    SCMRetriever retriever = (SCMRetriever) cfg.getRetriever();
                    if (retriever.getScm() instanceof BitbucketSCM && scm instanceof BitbucketSCM) {
                        BitbucketSCM libraryScm = (BitbucketSCM) retriever.getScm();
                        BitbucketSCM bitbucketScm = (BitbucketSCM) scm;
                        if (libraryScm.getId().equals(bitbucketScm.getId())) {
                            return;
                        }
                    }
                }
            }
        }
        BitbucketSCMRepository bitbucketSCMRepository = repositoryHelper.getRepository(build, scm);
        if (bitbucketSCMRepository == null) {
            return;
        }
        GitSCM underlyingScm = getUnderlyingGitSCM(scm);
        if (underlyingScm == null) {
            return;
        }
        Map<String, String> env = new HashMap<>();
        underlyingScm.buildEnvironment(build, env);

        String branch = env.get(GitSCM.GIT_BRANCH);
        String refName = branch != null ? underlyingScm.deriveLocalBranchName(branch) : null;
        BitbucketRevisionAction revisionAction =
                new BitbucketRevisionAction(bitbucketSCMRepository, refName, env.get(GitSCM.GIT_COMMIT));
        build.addAction(revisionAction);
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }
}
