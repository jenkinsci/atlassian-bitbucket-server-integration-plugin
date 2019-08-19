package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import javax.inject.Inject;

public class BitbucketPostBuildStatus extends GitSCMExtension {

    @Inject
    private BitbucketClientFactoryProvider clientFactoryProvider;
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;
    private final String serverId;

    @DataBoundConstructor
    public BitbucketPostBuildStatus(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                        CheckoutCommand cmd) throws GitException {
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

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Bitbucket Build Status Notifier";
        }

        @Override
        public BitbucketPostBuildStatus newInstance(@Nullable StaplerRequest req,
                                                    @Nonnull JSONObject formData) throws FormException {
            BitbucketPostBuildStatus extension = (BitbucketPostBuildStatus) super.newInstance(req, formData);
            return extension;
        }
    }
}
