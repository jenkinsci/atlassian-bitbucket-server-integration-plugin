package com.atlassian.bitbucket.jenkins.internal.listener;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import javax.inject.Inject;

@Extension
public class BitbucketBuildListener<R extends Run> extends RunListener<R> {

    @Inject
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;

    @Override
    public void onCompleted(R r, TaskListener listener) {
        if (r instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) r;
            if (build.getProject().getScm() instanceof BitbucketSCM) {
                BitbucketSCM scm = (BitbucketSCM) build.getProject().getScm();
                BitbucketServerConfiguration server = pluginConfiguration.getServerById(scm.getServerId())
                        .orElseThrow(() -> new BitbucketSCMException("The provided Bitbucket Server config does not exist"));
                BitbucketCredentials credentials = BitbucketCredentialsAdaptor.createWithFallback(server.getCredentials(), server);

                bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), credentials)
                        .getBuildStatusClient(scm.getLatestRevision(build))
                        .post(new BitbucketBuildStatus(build));
            }
        }
    }
}
