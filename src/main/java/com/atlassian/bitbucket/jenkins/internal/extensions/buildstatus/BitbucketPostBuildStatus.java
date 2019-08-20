package com.atlassian.bitbucket.jenkins.internal.extensions.buildstatus;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

public class BitbucketPostBuildStatus extends GitSCMExtension {

    private final String serverId;

    public BitbucketPostBuildStatus(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws GitException {
        BitbucketPluginConfiguration pluginConfiguration = Jenkins.get().getInjector().getInstance(BitbucketPluginConfiguration.class);
        BitbucketClientFactoryProvider clientFactoryProvider = Jenkins.get().getInjector().getInstance(BitbucketClientFactoryProvider.class);

        if (build instanceof AbstractBuild) {
            String revisionSha1 = scm.getBuildData(build).lastBuild.getRevision().getSha1String();
            BitbucketServerConfiguration server =
                    pluginConfiguration.getServerById(serverId)
                            .orElseThrow(() -> new RuntimeException("The provided Bitbucket Server config does not exist"));
            clientFactoryProvider.getClient(server.getBaseUrl(), BitbucketCredentialsAdaptor.createWithFallback(server.getCredentials(), server))
                    .getBuildStatusClient(revisionSha1)
                    .post(new BitbucketBuildStatus((AbstractBuild) build));
        }
    }
}
