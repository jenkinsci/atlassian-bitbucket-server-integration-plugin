package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.utils.CredentialUtils;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.inject.Inject;
import java.io.IOException;

public class BitbucketGitSCMExtension extends GitSCMExtension {

    @Inject
    BitbucketClientFactoryProvider clientFactoryProvider;
    @Inject
    BitbucketPluginConfiguration pluginConfiguration;
    private final String serverId;

    public BitbucketGitSCMExtension(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        if (build instanceof AbstractBuild) {
            String revisionSha1 = scm.getBuildData(build).lastBuild.getRevision().getSha1String();
            BitbucketServerConfiguration server =
                    pluginConfiguration.getServerById(serverId)
                            .orElseThrow(() -> new RuntimeException("Server config not found"));
            clientFactoryProvider.getClient(server, CredentialUtils.getCredentials(server.getCredentialsId()))
                    .getBuildStatusClient(revisionSha1)
                    .post(new BitbucketBuildStatus((AbstractBuild) build));
        }
    }
}
