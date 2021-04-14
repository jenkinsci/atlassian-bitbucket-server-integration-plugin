package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import hudson.Extension;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation.Kind;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.scm.api.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class BitbucketSCMFileSystem extends SCMFileSystem {

    private final BitbucketFilePathClient client;
    private final String ref;

    protected BitbucketSCMFileSystem(BitbucketFilePathClient client, @Nullable SCMRevision scmRevision, @Nullable String ref) {
        super(scmRevision);
        this.client = client;
        this.ref = ref;
    }

    @Override
    public SCMFile getRoot() {
        return new BitbucketSCMFile(client, ref);
    }

    // We do not provide this information in the REST response, so this is undefined.
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
            if (!maybeServerConfiguration.isPresent() || maybeServerConfiguration.get().validate().kind == Kind.ERROR ||
                bitbucketSCM.getBranchSpec() == null) {
                return null;
            }
            BitbucketSCMRepository repository = bitbucketSCM.getBitbucketSCMRepository();

            BitbucketRepositoryClient repositoryClient = clientFactoryProvider.getClient(maybeServerConfiguration.get().getBaseUrl(),
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(repository.getCredentialsId()))
                    .getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug());

            if (scmRevision == null) {
                List<BitbucketBranch> branchList = repositoryClient.getBranches();
                return branchList.stream()
                        .map(BitbucketBranch::getId)
                        .filter(ref -> bitbucketSCM.getBranchSpec()
                                .matchesRepositoryBranch(repository.getRepositoryName(), ref))
                        .findAny()
                        .map(ref -> new BitbucketSCMFileSystem(repositoryClient.getFilePathClient(), null, ref))
                        .orElse(null);
            }
            // Unsupported ref type. Lightweight checkout not supported
            return null;
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
            BitbucketSCMRepository repository = bitbucketSCMSource.getBitbucketSCMRepository();

            BitbucketFilePathClient filePathClient = clientFactoryProvider.getClient(maybeServerConfiguration.get().getBaseUrl(),
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(repository.getCredentialsId()))
                    .getProjectClient(repository.getProjectKey())
                    .getRepositoryClient(repository.getRepositorySlug())
                    .getFilePathClient();

            if (scmRevision.getHead() instanceof GitBranchSCMHead) {
                return new BitbucketSCMFileSystem(filePathClient, scmRevision, ((GitBranchSCMHead) scmRevision.getHead()).getRef());
            }
            // Unsupported ref type. Lightweight checkout not supported
            return null;
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

        private String getRefString(@CheckForNull SCMRevision revision) {
            if (revision == null) {
                // Cannot use FS, fall back to full checkout
                return null;
            }
            return null;
        }
    }
}
