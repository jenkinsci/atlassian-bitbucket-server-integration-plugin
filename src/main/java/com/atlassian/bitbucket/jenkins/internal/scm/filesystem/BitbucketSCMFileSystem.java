package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.eclipse.jgit.lib.Constants.*;

/**
 * @since 3.0.0
 */
public class BitbucketSCMFileSystem extends SCMFileSystem {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMFileSystem.class.getName());

    private final BitbucketFilePathClient client;
    private final String ref;

    protected BitbucketSCMFileSystem(BitbucketFilePathClient client, @Nullable SCMRevision scmRevision,
                                     @Nullable String ref) {
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
    public long lastModified() {
        return 0L;
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Inject
        BitbucketClientFactoryProvider clientFactoryProvider;
        @Inject
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
        @Inject
        BitbucketPluginConfiguration pluginConfiguration;

        // Pipeline SCM jobs will only build on lightweight if the branch selector points to a specific branch or tag
        // ref- otherwise, we default to full checkout
        @Override
        public SCMFileSystem build(Item item, SCM scm, @CheckForNull SCMRevision scmRevision) {
            if (!(scm instanceof BitbucketSCM)) {
                return null;
            }

            BitbucketSCM bitbucketSCM = (BitbucketSCM) scm;
            Optional<BitbucketServerConfiguration> maybeServerConfiguration =
                    pluginConfiguration.getServerById(bitbucketSCM.getServerId());
            if (!maybeServerConfiguration.isPresent() || maybeServerConfiguration.get().validate().kind == Kind.ERROR) {
                LOGGER.finer("ERROR: Bitbucket Server configuration for job " + item.getName() +
                             " is invalid- cannot build file system");
                return null;
            }

            BitbucketSCMRepository repository = bitbucketSCM.getBitbucketSCMRepository();

            BitbucketFilePathClient filePathClient =
                    clientFactoryProvider.getClient(maybeServerConfiguration.get().getBaseUrl(),
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(repository.getCredentialsId(), item))
                            .getProjectClient(repository.getProjectKey())
                            .getRepositoryClient(repository.getRepositorySlug())
                            .getFilePathClient();

            return new BitbucketSCMFileSystem(filePathClient, null, bitbucketSCM.getBranches().get(0).toString());
        }

        // FB supression due to a false positive on Item.getName (not recognizing we check for null)
        @Override
        @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        public SCMFileSystem build(SCMSource source, SCMHead head, @CheckForNull SCMRevision scmRevision) {
            if (!(source instanceof BitbucketSCMSource)) {
                return null;
            }
            BitbucketSCMSource bitbucketSCMSource = (BitbucketSCMSource) source;
            String ownerName = "";
            if (source.getOwner() != null && source.getOwner().getName() != null) {
                ownerName = source.getOwner().getName();
            }
            Optional<BitbucketServerConfiguration> maybeServerConfiguration =
                    pluginConfiguration.getServerById(bitbucketSCMSource.getServerId());
            if (!maybeServerConfiguration.isPresent() || maybeServerConfiguration.get().validate().kind == Kind.ERROR) {
                LOGGER.warning("ERROR: Bitbucket Server configuration for job " + ownerName +
                             " is invalid- cannot continue lightweight checkout");
                return null;
            }
            BitbucketSCMRepository repository = bitbucketSCMSource.getBitbucketSCMRepository();

            BitbucketFilePathClient filePathClient =
                    clientFactoryProvider.getClient(maybeServerConfiguration.get().getBaseUrl(),
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(repository.getCredentialsId(), source.getOwner()))
                            .getProjectClient(repository.getProjectKey())
                            .getRepositoryClient(repository.getRepositorySlug())
                            .getFilePathClient();

            if (scmRevision != null && scmRevision.getHead() instanceof GitBranchSCMHead) {
                return new BitbucketSCMFileSystem(filePathClient, scmRevision, ((GitBranchSCMHead) scmRevision.getHead()).getRef());
            }
            // Unsupported ref type. Lightweight checkout not supported
            LOGGER.finer("Lightweight checkout for Bitbucket SCM source only supported for Multibranch Pipeline jobs. " +
                         "Cannot build file system for job " + ownerName);
            return null;
        }

        @Override
        public boolean supports(SCM scm) {
            if (scm instanceof BitbucketSCM) {
                List<BranchSpec> branchSpecList = ((BitbucketSCM) scm).getBranches();
                if (branchSpecList.size() == 1 && branchSpecList.get(0).toString() != null && (
                        branchSpecList.get(0).toString().startsWith(R_HEADS) ||
                        branchSpecList.get(0).toString().startsWith(R_TAGS))) {
                    return true;
                }
                LOGGER.finer("Branch spec must be in the form 'refs/heads/<branchname>' or 'refs/tags/<tagname>'. " +
                             "Cannot build file system for this job.");
            }
            return false;
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
    }
}
