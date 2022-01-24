package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsModule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.inject.Guice;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.Stash;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketSCM extends SCM {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCM.class.getName());

    private GitSCM gitSCM;
    // avoid a difficult upgrade task.
    private final List<BranchSpec> branches;
    private final List<GitSCMExtension> extensions;
    private final String gitTool;
    private final String id;
    // this is to enable us to support future multiple repositories
    private final List<BitbucketSCMRepository> repositories;
    private volatile boolean isWebhookRegistered;

    @DataBoundConstructor
    public BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull String credentialsId,
            @CheckForNull String sshCredentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String projectName,
            @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE", justification = "We handle null values properly in a way that Findbugs misses")
            @CheckForNull String repositoryName,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName) {

        // This is a temporary storage of the SCM details as the deserialized stapler request is not provided with the
        // parent object
        this(id, branches, extensions, gitTool);
        repositories.add(new BitbucketSCMRepository(
                credentialsId, sshCredentialsId, projectName, projectName, repositoryName, repositoryName, serverId, mirrorName));
    }

    // This constructor is to be used when building an SCM in code but the GitSCM needs to be initialized as part of the
    // construction, rather than automatically when performing a checkout or other git operation. This requires the user
    // to provide a credential context, if one is available.
    public BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull String credentialsId,
            @CheckForNull String sshCredentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String projectName,
            @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE", justification = "We handle null values properly in a way that Findbugs misses")
            @CheckForNull String repositoryName,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName,
            @CheckForNull Item context) {

        this(id, branches, credentialsId, sshCredentialsId, extensions, gitTool, projectName, repositoryName, serverId, mirrorName);
        initializeGitScm(context);
    }

    public BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull String credentialsId,
            @CheckForNull String sshCredentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String serverId,
            BitbucketRepository repository) {

        // This is a temporary storage of the SCM details as the deserialized stapler request is not provided with the
        // parent object
        this(id, branches, extensions, gitTool);
        repositories.add(new BitbucketSCMRepository(
                credentialsId, sshCredentialsId, repository.getProject().getName(), repository.getProject().getKey(), 
                repository.getName(), repository.getSlug(), serverId, null));
    }

    /**
     * Regenerate SCM by looking up new repo URLs etc.
     *
     * @param oldScm old scm to copy values from
     */
    public BitbucketSCM(BitbucketSCM oldScm) {
        this(oldScm.getId(), oldScm.getBranches(), oldScm.getCredentialsId(), oldScm.getSshCredentialsId(), oldScm.getExtensions(),
                oldScm.getGitTool(), oldScm.getProjectName(), oldScm.getRepositoryName(), oldScm.getServerId(),
                oldScm.getMirrorName());
    }

    private BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool) {
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        this.branches = new ArrayList<>();
        this.extensions = new ArrayList<>();
        this.gitTool = gitTool;
        repositories = new ArrayList<>(1);

        if (branches != null) {
            this.branches.addAll(branches);
        }
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
    }

    @CheckForNull
    public String getGitTool() {
        return gitTool;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, Map<String, String> env) {
        getAndInitializeGitScmIfNull(build.getParent())
                .buildEnvironment(build, env);
    }

    @CheckForNull
    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            Run<?, ?> build,
            @Nullable FilePath workspace,
            @Nullable Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        return getAndInitializeGitScmIfNull(build.getParent())
                .calcRevisionsFromBuild(build, workspace, launcher, listener);
    }

    @Override
    public void checkout(
            Run<?, ?> build,
            Launcher launcher,
            FilePath workspace,
            TaskListener listener,
            @CheckForNull File changelogFile,
            @CheckForNull SCMRevisionState baseline)
            throws IOException, InterruptedException {
        getAndInitializeGitScmIfNull(build.getParent()) 
                .checkout(build, launcher, workspace, listener, changelogFile, baseline);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(
            Job<?, ?> project,
            @Nullable Launcher launcher,
            @Nullable FilePath workspace,
            TaskListener listener,
            SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return getAndInitializeGitScmIfNull(project)
                .compareRemoteRevisionWith(project, launcher, workspace, listener, baseline);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return gitSCM.createChangeLogParser();
    }

    public List<BranchSpec> getBranches() {
        if (gitSCM == null) {
            return branches;
        }
        return gitSCM.getBranches();
    }

    @CheckForNull
    @Override
    public RepositoryBrowser<?> getBrowser() {
        return gitSCM.getBrowser();
    }

    @CheckForNull
    public GitSCM getGitSCM() {
        return gitSCM;
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    @CheckForNull
    public String getSshCredentialsId() {
        return getBitbucketSCMRepository().getSshCredentialsId();
    }

    public List<GitSCMExtension> getExtensions() {
        if (gitSCM == null) {
            return emptyList();
        }
        return gitSCM.getExtensions();
    }

    public String getId() {
        return id;
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        BitbucketSCMRepository repository = getBitbucketSCMRepository();
        return repository.isPersonal() ? repository.getProjectKey() : repository.getProjectName();
    }

    public List<BitbucketSCMRepository> getRepositories() {
        return repositories;
    }

    public String getRepositorySlug() {
        return getBitbucketSCMRepository().getRepositorySlug();
    }

    public String getRepositoryName() {
        return getBitbucketSCMRepository().getRepositoryName();
    }

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
    }

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    public List<UserRemoteConfig> getUserRemoteConfigs() {
        if (gitSCM == null) {
            return emptyList();
        }
        return gitSCM.getUserRemoteConfigs();
    }

    public void setWebhookRegistered(boolean isWebhookRegistered) {
        this.isWebhookRegistered = isWebhookRegistered;
    }

    public boolean isWebhookRegistered() {
        return isWebhookRegistered;
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
    }
    
    public GitSCM getAndInitializeGitScmIfNull(@Nullable Item context) {
        if (gitSCM == null) {
            initializeGitScm(context);
        }
        return gitSCM;
    }

    private void initializeGitScm(@Nullable Item context) {
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(getServerId());
        if (!mayBeServerConf.isPresent()) {
            return;
        }
        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        String selfLink, cloneUrl;
        
        Optional<Credentials> maybeCredentials = CredentialUtils.getCredentials(getCredentialsId(), context);
        BitbucketScmHelper scmHelper =
                descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(), maybeCredentials.orElse(null));
        
        if (getBitbucketSCMRepository().isMirrorConfigured()) {
            EnrichedBitbucketMirroredRepository fetchedRepository =
                    descriptor.createMirrorHandler(scmHelper)
                            .fetchRepository(
                                    new MirrorFetchRequest(
                                            serverConfiguration.getBaseUrl(),
                                            context,
                                            getCredentialsId(),
                                            getProjectName(),
                                            getRepositoryName(),
                                            getMirrorName()));
            BitbucketRepository underlyingRepo = fetchedRepository.getRepository();
            repositories.set(0, new BitbucketSCMRepository(getCredentialsId(), getSshCredentialsId(),
                    underlyingRepo.getProject().getName(), underlyingRepo.getProject().getKey(), underlyingRepo.getName(),
                    underlyingRepo.getSlug(), getServerId(), fetchedRepository.getMirroringDetails().getMirrorName()));
            cloneUrl = underlyingRepo.getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                    .map(BitbucketNamedLink::getHref).orElse("");
            selfLink = fetchedRepository.getRepository().getSelfLink();
        } else {
            BitbucketRepository fetchedRepository = scmHelper.getRepository(getProjectName(), getRepositoryName());
            repositories.set(0, new BitbucketSCMRepository(getCredentialsId(), getSshCredentialsId(),
                    fetchedRepository.getProject().getName(), fetchedRepository.getProject().getKey(),
                    fetchedRepository.getName(), fetchedRepository.getSlug(), getServerId(), ""));
            cloneUrl = fetchedRepository.getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                    .map(BitbucketNamedLink::getHref).orElse("");
            selfLink = fetchedRepository.getSelfLink();
        }

        // Self link contains `/browse` which we must trim off.
        selfLink = selfLink.substring(0, max(selfLink.lastIndexOf("/browse"), 0));
        if (isBlank(cloneUrl)) {
            LOGGER.info("No clone url found for repository: " + getRepositoryName());
        }

        // Initialize the Git SCM
        UserRemoteConfig remoteConfig = new UserRemoteConfig(cloneUrl, getRepositorySlug(), null, 
                getBitbucketSCMRepository().getCloneCredentialsId());
        gitSCM = new GitSCM(singletonList(remoteConfig), branches, new Stash(selfLink), gitTool, extensions);
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"unused"})
    public static class DescriptorImpl extends SCMDescriptor<BitbucketSCM> implements BitbucketScmFormValidation,
            BitbucketScmFormFill {

        private final GitSCM.DescriptorImpl gitScmDescriptor;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        private transient JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        public DescriptorImpl() {
            super(Stash.class);
            gitScmDescriptor = new GitSCM.DescriptorImpl();
            load();
        }

        @Override
        @POST
        public FormValidation doCheckCredentialsId(@AncestorInPath Item context,
                                                   @QueryParameter String credentialsId) {
            return formValidation.doCheckCredentialsId(context, credentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckSshCredentialsId(@AncestorInPath Item context,
                                                      @QueryParameter String sshCredentialsId) {
            return formValidation.doCheckCredentialsId(context, sshCredentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckProjectName(@AncestorInPath Item context,
                                                 @QueryParameter String serverId,
                                                 @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            return formValidation.doCheckProjectName(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public FormValidation doCheckRepositoryName(@AncestorInPath Item context,
                                                    @QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            return formValidation.doCheckRepositoryName(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public FormValidation doCheckServerId(@AncestorInPath Item context,
                                              @QueryParameter String serverId) {
            return formValidation.doCheckServerId(context, serverId);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            return formFill.doFillCredentialsIdItems(context, baseUrl, credentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item context,
                                                        @QueryParameter String baseUrl,
                                                        @QueryParameter String sshCredentialsId) {
            return formFill.doFillSshCredentialsIdItems(context, baseUrl, sshCredentialsId);
        }

        @Override
        @POST
        public FormValidation doTestConnection(@AncestorInPath Item context,
                                               @QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        @POST
        public ListBoxModel doFillGitToolItems() {
            return gitScmDescriptor.doFillGitToolItems();
        }

        @Override
        @POST
        public HttpResponse doFillProjectNameItems(@AncestorInPath Item context,
                                                   @QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            return formFill.doFillProjectNameItems(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public HttpResponse doFillRepositoryNameItems(@AncestorInPath Item context,
                                                      @QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            return formFill.doFillRepositoryNameItems(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public ListBoxModel doFillServerIdItems(@AncestorInPath Item context, @QueryParameter String serverId) {
            return formFill.doFillServerIdItems(context, serverId);
        }

        @Override
        @POST
        public ListBoxModel doFillMirrorNameItems(@AncestorInPath Item context,
                                                  @QueryParameter String serverId,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String projectName,
                                                  @QueryParameter String repositoryName,
                                                  @QueryParameter String mirrorName) {
            return formFill.doFillMirrorNameItems(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket Server";
        }

        @Override
        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return gitScmDescriptor.getExtensionDescriptors();
        }

        @Override
        public List<GitTool> getGitTools() {
            return gitScmDescriptor.getGitTools();
        }

        @Override
        public boolean getShowGitToolOptions() {
            return gitScmDescriptor.showGitToolOptions();
        }

        @Override
        public boolean isApplicable(Job project) {
            return true;
        }

        @Inject
        public void setJenkinsToBitbucketCredentials(
                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
            this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        }

        BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl,
                                                 @CheckForNull Credentials httpCredentials) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(httpCredentials));
        }

        private BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }

        Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }

        public void injectJenkinsToBitbucketCredentials() {
            if (jenkinsToBitbucketCredentials == null) {
                Guice.createInjector(new JenkinsToBitbucketCredentialsModule()).injectMembers(this);
            }
        }

        @Override
        public SCM newInstance(@Nullable StaplerRequest req, JSONObject formData) throws FormException {
            BitbucketSCM instance = (BitbucketSCM) super.newInstance(req, formData);
            
            if (req != null) {
                if (!req.getAncestors().isEmpty()) {
                    Ancestor parent = req.getAncestors().get(req.getAncestors().size() - 1);
                    instance.initializeGitScm(parent.getObject() instanceof Item ? (Item) parent.getObject() : null);
                } else {
                    instance.initializeGitScm(null);
                }
            }
            return instance;
        }
    }
}
