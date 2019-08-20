package com.atlassian.bitbucket.jenkins.internal.extensions.buildstatus;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.google.inject.Injector;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.util.Optional;

public class BitbucketPostBuildStatus extends GitSCMExtension {

    private final String serverId;

    public BitbucketPostBuildStatus(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws GitException {
        Injector injector = Optional.ofNullable(Jenkins.get().getInjector()).orElseThrow(RuntimeException::new);
        BitbucketPluginConfiguration pluginConfiguration =
                injector.getInstance(BitbucketPluginConfiguration.class);
        BitbucketClientFactoryProvider clientFactoryProvider =
                injector.getInstance(BitbucketClientFactoryProvider.class);

        if (build instanceof AbstractBuild) {
            String revisionSha1 =
                    Optional.ofNullable(scm.getBuildData(build)).orElseThrow(RuntimeException::new).lastBuild.getRevision().getSha1String();
            BitbucketServerConfiguration server =
                    pluginConfiguration.getServerById(serverId)
                            .orElseThrow(() -> new RuntimeException("The provided Bitbucket Server config does not exist"));
            clientFactoryProvider.getClient(server.getBaseUrl(), BitbucketCredentialsAdaptor.createWithFallback(server.getCredentials(), server))
                    .getBuildStatusClient(revisionSha1)
                    .post(new BitbucketBuildStatus((AbstractBuild) build));
        }
    }
}
