package com.atlassian.bitbucket.jenkins.internal.listener;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import com.atlassian.bitbucket.jenkins.internal.utils.CredentialUtils;
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
        if (r instanceof FreeStyleBuild) {
            FreeStyleBuild build = (FreeStyleBuild) r;
            if (build.getProject().getScm() instanceof BitbucketSCM) {
                BitbucketSCM scm = (BitbucketSCM) build.getProject().getScm();
                BitbucketServerConfiguration server = pluginConfiguration.getServerById(scm.getServerId())
                        .orElseThrow(() -> new BitbucketSCMException("Error here"));
                bitbucketClientFactoryProvider.getClient(server, CredentialUtils.getCredentials(server.getCredentialsId()))
                        .getBuildStatusClient(scm.getLatestRevision(build))
                        .post(new BitbucketBuildStatus(build));
            }
        }
    }
}
