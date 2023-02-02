package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.provider.GlobalLibrariesProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepositoryHelper;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.DescribableList;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Extension
public class LocalSCMListener extends SCMListener {

    private BuildStatusPoster buildStatusPoster;
    private GlobalLibrariesProvider librariesProvider;
    private BitbucketSCMRepositoryHelper repositoryHelper;

    public LocalSCMListener() {
    }

    @Inject
    LocalSCMListener(BuildStatusPoster buildStatusPoster, GlobalLibrariesProvider librariesProvider,
                     BitbucketSCMRepositoryHelper repositoryHelper) {
        this.buildStatusPoster = buildStatusPoster;
        this.librariesProvider = librariesProvider;
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
        // Check if the current SCM we are checking out is configured as a folder library SCM
        if (build.getParent().getParent() instanceof Folder && isFolderLib((Folder) build.getParent().getParent(), scm)) {
            return;
        }
        if (build.getParent().getParent() instanceof WorkflowMultiBranchProject &&
                projectHasFolderLibrary((WorkflowMultiBranchProject) build.getParent().getParent(), scm)) {
            return;
        }

        // Check if the current SCM we are checking out is configured as a global library SCM
        for (LibraryConfiguration cfg : librariesProvider.get().getLibraries()) {
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

    private boolean isFolderLib(Folder folder, SCM scm) {
        if (isScmFolderLibrary(scm, folder.getProperties())) {
            return true;
        }
        if (folder.getParent() instanceof Folder) {
            // Recursively check parent folders for folder libraries
            return isFolderLib((Folder) folder.getParent(), scm);
        }
        return false;
    }

    private boolean isScmFolderLibrary(SCM scm,
                                       DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties) {
        for (Object folderItem : properties) {
            if (folderItem instanceof FolderLibraries) {
                FolderLibraries folderLibraries = (FolderLibraries) folderItem;
                for (LibraryConfiguration folderLib : folderLibraries.getLibraries()) {
                    if (folderLib.getRetriever() instanceof SCMRetriever) {
                        SCMRetriever retriever = (SCMRetriever) folderLib.getRetriever();
                        if (retriever.getScm() instanceof BitbucketSCM && scm instanceof BitbucketSCM) {
                            BitbucketSCM libraryScm = (BitbucketSCM) retriever.getScm();
                            BitbucketSCM bitbucketScm = (BitbucketSCM) scm;
                            if (libraryScm.getId().equals(bitbucketScm.getId())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean projectHasFolderLibrary(WorkflowMultiBranchProject project, SCM scm) {
        if (isScmFolderLibrary(scm, project.getProperties())) {
            return true;
        }
        if (project.getParent() instanceof Folder) {
            return isFolderLib((Folder) project.getParent(), scm);
        }
        return false;
    }
}
