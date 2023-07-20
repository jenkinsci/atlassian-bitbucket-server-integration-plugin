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
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketPullRequestSourceBranch.PULL_REQUEST_SOURCE_BRANCH;
import static com.atlassian.bitbucket.jenkins.internal.scm.BitbucketPullRequestSourceBranch.PULL_REQUEST_SOURCE_COMMIT;

@Extension
public class LocalSCMListener extends SCMListener {

    public static final String BRANCH_PREFIX = "refs/heads/";
    public static final String TAG_PREFIX = "refs/tags/";

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

        String refName = getRefFromEnvironment(env, underlyingScm);
        BitbucketRevisionAction revisionAction =
                new BitbucketRevisionAction(bitbucketSCMRepository, refName, getCommitFromEnvironment(env));
        build.addAction(revisionAction);
        buildStatusPoster.postBuildStatus(revisionAction, build, listener);
    }

    private String getCommitFromEnvironment(Map<String, String> env) {
        // Pull requests may be built using a merge between the source and target branches which could result in a new
        // commit for the merge. This new merge commit will not exist in Bitbucket, so we need to use the pull request
        // source commit when posting the build status so that Bitbucket can correctly resolve it.
        String commit = env.get(PULL_REQUEST_SOURCE_COMMIT);
        if (commit == null) {
            commit = env.get(GitSCM.GIT_COMMIT);
        }

        return commit;
    }

    @CheckForNull
    private String getRefFromEnvironment(Map<String, String> env, GitSCM scm) {
        // If the pull request source branch is specified use that as the ref ID
        String refId = StringUtils.stripToNull(env.get(PULL_REQUEST_SOURCE_BRANCH));

        // Otherwise, use the default value from GIT_BRANCH
        if (refId == null) {
            refId = StringUtils.stripToNull(env.get(GitSCM.GIT_BRANCH));
        }

        if (refId == null) {
            return null;
        }

        // The GitSCM will treat the tag as a branch with a fully qualified name, so if refs/tags/ is present,
        // the name needs no further processing.
        if (refId.startsWith(TAG_PREFIX)) {
            return refId;
        }

        // Branches are in the form of the result of a git fetch, prepended with the repository name. The Git SCM
        // can strip the repo name (if it's found in the list of remote configs), and we append refs/heads afterwards.
        return BRANCH_PREFIX + scm.deriveLocalBranchName(refId);
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
