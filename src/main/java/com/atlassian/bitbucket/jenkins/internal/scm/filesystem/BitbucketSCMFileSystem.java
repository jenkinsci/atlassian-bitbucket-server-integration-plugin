package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation.Kind;
import jenkins.scm.api.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;

public class BitbucketSCMFileSystem extends SCMFileSystem {

    private final BitbucketFilePathClient client;
    private final String ref;

    protected BitbucketSCMFileSystem(SCMRevision scmRevision, BitbucketFilePathClient client, String ref) {
        super(scmRevision);
        this.client = client;
        this.ref = ref;
    }

    @Override
    public SCMFile getRoot() {
        return new BitbucketSCMFile(client, ref);
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0;
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Inject
        BitbucketClientFactoryProvider clientFactoryProvider;
        @Inject
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
        @Inject
        BitbucketPluginConfiguration pluginConfiguration;

        @Override
        public SCMFileSystem build(Item item, SCM scm,
                                   SCMRevision scmRevision) throws IOException, InterruptedException {
            if (!(scm instanceof BitbucketSCM)) {
                return null;
            }
            BitbucketSCM bitbucketSCM = (BitbucketSCM) scm;
            Optional<BitbucketServerConfiguration> maybeServerConfiguration =
                    pluginConfiguration.getServerById(bitbucketSCM.getServerId());
            if (!maybeServerConfiguration.isPresent() || maybeServerConfiguration.get().validate().kind == Kind.ERROR) {
                return null;
            }

            return buildFromConfiguration(bitbucketSCM.getBitbucketSCMRepository(), scmRevision, maybeServerConfiguration.get());
        }

        @Override
        public SCMFileSystem build(SCMSource source, SCMHead head,
                                   SCMRevision scmRevision) throws IOException, InterruptedException {
            if (!(source instanceof BitbucketSCMSource)) {
                return null;
            }
            BitbucketSCMSource bitbucketSCMSource = (BitbucketSCMSource) source;
            Optional<BitbucketServerConfiguration> maybeServerConfiguration =
                    pluginConfiguration.getServerById(bitbucketSCMSource.getServerId());
            if (!maybeServerConfiguration.isPresent() || maybeServerConfiguration.get().validate().kind == Kind.ERROR) {
                return null;
            }

            return buildFromConfiguration(bitbucketSCMSource.getBitbucketSCMRepository(), scmRevision, maybeServerConfiguration.get());
        }

        @Override
        public boolean supports(SCM scm) {
            return scm instanceof BitbucketSCM;
        }

        @Override
        public boolean supports(SCMSource scmSource) {
            return scmSource instanceof BitbucketSCMSource;
        }

        @Override
        protected boolean supportsDescriptor(SCMDescriptor scmDescriptor) {
            return scmDescriptor instanceof BitbucketSCM.DescriptorImpl;
        }

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor scmSourceDescriptor) {
            return scmSourceDescriptor instanceof BitbucketSCMSource.DescriptorImpl;
        }

        private SCMFileSystem buildFromConfiguration(BitbucketSCMRepository repository, SCMRevision scmRevision,
                                                     BitbucketServerConfiguration serverConfiguration) {

            BitbucketFilePathClient client = clientFactoryProvider.getClient(serverConfiguration.getBaseUrl(),
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(repository.getCredentialsId()))
                    .getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug())
                    .getFilePathClient();

            // TODO: Test against multiple "HEAD" types- ensure this is working for all pushes
            return new BitbucketSCMFileSystem(scmRevision, client, scmRevision.getHead());
        }
    }
}
