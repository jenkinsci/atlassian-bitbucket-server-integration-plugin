package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.provider.DefaultJenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.status.BuildStatusPoster;
import com.google.inject.Injector;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.logging.Logger;

class BitbucketPostBuildStatus extends GitSCMExtension {

    private static final Logger LOGGER = Logger.getLogger(BuildStatusPoster.class.getName());
    private final JenkinsProvider jenkinsProvider;
    private final String serverId;

    public BitbucketPostBuildStatus(String serverId, JenkinsProvider jenkinsProvider) {
        this.serverId = serverId;
        this.jenkinsProvider = jenkinsProvider;
    }

    public BitbucketPostBuildStatus(String serverId) {
        this(serverId, new DefaultJenkinsProvider());
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws GitException {
        Injector injector = jenkinsProvider.get().getInjector();
        if (injector == null) {
            LOGGER.warning("Injector could not be found while creating build status");
        }

        if (build instanceof AbstractBuild) {
            BuildData buildData = scm.getBuildData(build);
            if (buildData == null || buildData.lastBuild == null) {
                return;
            }
            String revisionSha1 = buildData.lastBuild.getRevision().getSha1String();
            build.addAction(new BitbucketRevisionAction(revisionSha1, serverId));
            BuildStatusPoster poster = injector.getInstance(BuildStatusPoster.class);
            if (poster != null) {
                poster.postBuildStatus((AbstractBuild) build, listener);
            }
        }
    }
}
