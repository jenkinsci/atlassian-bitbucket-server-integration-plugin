package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor.createWithFallback;
import static hudson.security.Permission.CONFIGURE;
import static hudson.util.HttpResponses.okJSON;
import static java.lang.Math.max;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.kohsuke.stapler.HttpResponses.errorWithoutStack;

public class BitbucketSCM extends SCM {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCM.class.getName());

    // avoid a difficult upgrade task.
    private final List<BranchSpec> branches;
    private final List<GitSCMExtension> extensions;
    private final String gitTool;
    private final String id;
    // this is to enable us to support future multiple repositories
    private final List<BitbucketSCMRepository> repositories;

    private transient BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private transient BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private GitSCM gitSCM;

    @DataBoundConstructor
    public BitbucketSCM(
            String id,
            List<BranchSpec> branches,
            @CheckForNull List<GitSCMExtension> extensions,
            String gitTool,
            String serverId) {
        if (isBlank(serverId)) {
            LOGGER.info("Error creating the Bitbucket SCM: the server ID must not be blank");
        }
        this.branches = branches;
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        this.extensions = new ArrayList<>();
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
        this.extensions.add(new BitbucketPostBuildStatus(serverId));
        this.gitTool = gitTool;
        repositories = new ArrayList<>(1);
    }

    public void addRepositories(BitbucketSCMRepository... repositories) {
        this.repositories.addAll(Arrays.asList(repositories));
    }

    @CheckForNull
    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            Run<?, ?> build,
            @Nullable FilePath workspace,
            @Nullable Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        return gitSCM.calcRevisionsFromBuild(build, workspace, launcher, listener);
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
        gitSCM.checkout(build, launcher, workspace, listener, changelogFile, baseline);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(
            Job<?, ?> project,
            @Nullable Launcher launcher,
            @Nullable FilePath workspace,
            TaskListener listener,
            SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return gitSCM.compareRemoteRevisionWith(project, launcher, workspace, listener, baseline);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return gitSCM.createChangeLogParser();
    }

    public void createGitSCM() {
        BitbucketSCMRepository scmRepository = getBitbucketSCMRepository();
        BitbucketServerConfiguration server = getServer();
        String repositorySlug = scmRepository.getRepositorySlug();
        String credentialsId = scmRepository.getCredentialsId();
        BitbucketRepository repo;
        try {
            repo = getRepository(server, scmRepository.getProjectKey(), repositorySlug, credentialsId);
        } catch (BitbucketClientException e) {
            LOGGER.info("Error creating the Bitbucket SCM. Reason: " + firstNonBlank(e.getMessage(), "unknown"));
            repo = new BitbucketRepository(scmRepository.getRepositoryName(), null,
                    new BitbucketProject(scmRepository.getProjectKey(), null, scmRepository.getProjectName()),
                    scmRepository.getRepositorySlug(), RepositoryState.AVAILABLE);
        }
        UserRemoteConfig remoteConfig = new UserRemoteConfig(getCloneUrl(repo), repositorySlug, null,
                pickCredentialsId(server, credentialsId));
        gitSCM = new GitSCM(Collections.singletonList(remoteConfig), branches, false, Collections.emptyList(),
                new Stash(getRepositoryUrl(repo)), gitTool, extensions);
    }

    public List<BranchSpec> getBranches() {
        return gitSCM.getBranches();
    }

    @CheckForNull
    @Override
    public RepositoryBrowser<?> getBrowser() {
        return gitSCM.getBrowser();
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    public List<GitSCMExtension> getExtensions() {
        return gitSCM.getExtensions().stream().filter(extension -> extension.getClass() !=
                                                                   BitbucketPostBuildStatus.class).collect(Collectors.toList());
    }

    public String getGitTool() {
        return gitTool;
    }

    public String getId() {
        return id;
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        return getBitbucketSCMRepository().getProjectName();
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

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    public void setBitbucketClientFactoryProvider(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
    }

    public void setBitbucketPluginConfiguration(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    private static String getCloneUrl(BitbucketRepository repo) {
        return repo.getCloneUrls()
                .stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .orElseThrow(() -> new BitbucketClientException("No HttpClone url", -1, null))
                .getHref();
    }

    private static String getRepositoryUrl(BitbucketRepository repository) {
        String selfLink = repository.getSelfLink(); // self-link include /browse which needs to be trimmed
        return selfLink.substring(0, max(selfLink.indexOf("/browse"), 0));
    }

    private BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
    }

    private BitbucketRepository getRepository(BitbucketServerConfiguration server, String projectKey,
                                              String repositorySlug, @Nullable String credentialsId) {
        return bitbucketClientFactoryProvider
                .getClient(server.getBaseUrl(), createWithFallback(credentialsId, server))
                .getProjectClient(projectKey)
                .getRepositoryClient(repositorySlug)
                .getRepository();
    }

    private BitbucketServerConfiguration getServer() {
        return bitbucketPluginConfiguration
                .getServerById(getBitbucketSCMRepository().getServerId())
                .orElseThrow(() -> new RuntimeException("Server config not found"));
    }

    @Nullable
    private String pickCredentialsId(BitbucketServerConfiguration serverConfiguration, @Nullable String credentialsId) {
        return credentialsId != null ? credentialsId : serverConfiguration.getCredentialsId();
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"MethodMayBeStatic", "unused"})
    public static class DescriptorImpl extends SCMDescriptor<BitbucketSCM> {

        private final GitSCM.DescriptorImpl gitScmDescriptor;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        private BitbucketPage<BitbucketProject> latestProjects = new BitbucketPage<>();
        private BitbucketPage<BitbucketRepository> latestRepositories = new BitbucketPage<>();

        public DescriptorImpl() {
            super(Stash.class);
            gitScmDescriptor = new GitSCM.DescriptorImpl();
            load();
        }

        @POST
        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
            if (!isBlank(credentialsId) && providedCredentials == null) {
                return FormValidation.error("No credentials exist for the provided credentialsId");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckProjectName(@QueryParameter String serverId,
                                                 @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            Jenkins.get().checkPermission(CONFIGURE);
            if (isBlank(projectName)) {
                return FormValidation.error("Please specify a project name.");
            }
            Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
            if (!isBlank(credentialsId) && providedCredentials == null) {
                return FormValidation.ok(); // There will be an error in the credentials field
            }

            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> {
                        try {
                            BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider
                                    .getClient(serverConf.getBaseUrl(), createWithFallback(providedCredentials, serverConf));
                            BitbucketProject project = getProjectByNameOrKey(projectName, clientFactory);
                            return FormValidation.ok("Using '" + project.getName() + "' at " + project.getSelfLink());
                        } catch (NotFoundException e) {
                            return FormValidation.error("The project '" + projectName + "' does not exist or " +
                                                        "you do not have permission to access it.");
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            return FormValidation.error("Something went wrong when trying to contact " +
                                                        "Bitbucket Server: " + e.getMessage());
                        }
                    }).orElse(FormValidation.ok()); // There will be an error on the server field
        }

        @POST
        public FormValidation doCheckRepositoryName(@QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            Jenkins.get().checkPermission(CONFIGURE);
            if (isBlank(projectName)) {
                return FormValidation.ok(); // There will be an error on the projectName field
            }
            Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
            if (!isBlank(credentialsId) && providedCredentials == null) {
                return FormValidation.ok(); // There will be an error in the credentials field
            }
            if (isEmpty(repositoryName)) {
                return FormValidation.error("Please specify a repository name.");
            }

            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> {
                        try {
                            BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider
                                    .getClient(serverConf.getBaseUrl(), createWithFallback(providedCredentials, serverConf));
                            BitbucketRepository repository = getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
                            return FormValidation.ok("Using '" + repository.getName() + "' at " + repository.getSelfLink());
                        } catch (NotFoundException e) {
                            return FormValidation.error("The repository '" + repositoryName + "' does not " +
                                                        "exist or you do not have permission to access it.");
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            return FormValidation.error("Something went wrong when trying to contact " +
                                                        "Bitbucket Server: " + e.getMessage());
                        }
                    }).orElse(FormValidation.ok()); // There will be an error on the server field
        }

        @POST
        public FormValidation doCheckServerId(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(CONFIGURE);
            // Users can only demur in providing a server name if none are available to select
            if (bitbucketPluginConfiguration.getValidServerList().stream().noneMatch(server -> server.getId().equals(serverId))) {
                return FormValidation.error("Please specify a valid Bitbucket Server.");
            }
            if (bitbucketPluginConfiguration.hasAnyInvalidConfiguration()) {
                return FormValidation.warning("Some servers have been incorrectly configured, and are not displayed.");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("MethodMayBeStatic")
        @POST
        public ListBoxModel doFillCredentialsIdItems(
                @QueryParameter String baseUrl, @QueryParameter String credentialsId) {
            Jenkins instance = Jenkins.get();
            instance.checkPermission(CONFIGURE);
            if (!instance.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            StringCredentials.class,
                            URIRequirementBuilder.fromUri(baseUrl).build(),
                            CredentialsMatchers.always())
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            StandardUsernamePasswordCredentials.class,
                            URIRequirementBuilder.fromUri(baseUrl).build(),
                            CredentialsMatchers.always());
        }

        @POST
        public ListBoxModel doFillGitToolItems() {
            Jenkins.get().checkPermission(CONFIGURE);
            return gitScmDescriptor.doFillGitToolItems();
        }

        @POST
        public HttpResponse doFillProjectNameItems(@QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            Jenkins.get().checkPermission(CONFIGURE);
            if (isBlank(serverId)) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "A Bitbucket Server serverId must be provided");
            }
            if (stripToEmpty(projectName).length() < 2) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "The project name must be at least 2 characters long");
            }

            Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
            if (!isBlank(credentialsId) && providedCredentials == null) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "No credentials exist for the provided credentialsId");
            }

            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> {
                        try {
                            BitbucketCredentials credentials = createWithFallback(providedCredentials, serverConf);
                            BitbucketSearchClient searchClient = bitbucketClientFactoryProvider
                                    .getClient(serverConf.getBaseUrl(), credentials)
                                    .getSearchClient(stripToEmpty(projectName));
                            latestProjects = searchClient.findProjects();
                            return okJSON(JSONObject.fromObject(latestProjects));
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            LOGGER.info(e.getMessage());
                            return errorWithoutStack(HTTP_INTERNAL_ERROR, "An error occurred in Bitbucket: " + e.getMessage());
                        }
                    }).orElseGet(() -> errorWithoutStack(HTTP_BAD_REQUEST, "The provided Bitbucket Server serverId does not exist"));
        }

        @POST
        public HttpResponse doFillRepositoryNameItems(@QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            Jenkins.get().checkPermission(CONFIGURE);
            if (isBlank(serverId)) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "A Bitbucket Server serverId must be provided");
            }
            if (stripToEmpty(repositoryName).length() < 2) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "The repository name must be at least 2 characters long");
            }
            if (isBlank(projectName)) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "The projectName must be present");
            }

            Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
            if (!isBlank(credentialsId) && providedCredentials == null) {
                return errorWithoutStack(HTTP_BAD_REQUEST, "No credentials exist for the provided credentialsId");
            }

            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> {
                        BitbucketCredentials credentials = createWithFallback(providedCredentials, serverConf);
                        BitbucketSearchClient searchClient = bitbucketClientFactoryProvider
                                .getClient(serverConf.getBaseUrl(), credentials)
                                .getSearchClient(projectName);
                        try {
                            latestRepositories = searchClient.findRepositories(stripToEmpty(repositoryName));
                            return okJSON(JSONObject.fromObject(latestRepositories));
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            LOGGER.info(e.getMessage());
                            return errorWithoutStack(HTTP_INTERNAL_ERROR, "An error occurred in Bitbucket: " + e.getMessage());
                        }
                    }).orElseGet(() -> errorWithoutStack(HTTP_BAD_REQUEST, "The provided Bitbucket Server serverId does not exist"));
        }

        @POST
        public ListBoxModel doFillServerIdItems(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(CONFIGURE);
            //Filtered to only include valid server configurations
            StandardListBoxModel model =
                    bitbucketPluginConfiguration.getServerList()
                            .stream()
                            .filter(server -> server.getId().equals(serverId) ||
                                              server.validate().kind == FormValidation.Kind.OK)
                            .map(server ->
                                    new Option(
                                            server.getServerName(),
                                            server.getId(),
                                            server.getId().equals(serverId)))
                            .collect(toCollection(StandardListBoxModel::new));
            if (model.isEmpty() || model.stream().noneMatch(server -> server.value.equals(serverId))) {
                model.includeEmptyValue();
            }
            return model;
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket Server";
        }

        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return gitScmDescriptor.getExtensionDescriptors();
        }

        public List<GitTool> getGitTools() {
            return gitScmDescriptor.getGitTools();
        }

        public boolean getShowGitToolOptions() {
            return gitScmDescriptor.showGitToolOptions();
        }

        /**
         * Overridden to provide a better experience for errors.
         *
         * @param req      request
         * @param formData json data
         * @return a new BitbucketSCM instance
         * @throws FormException if any data is missing
         */
        @Override
        public SCM newInstance(@Nullable StaplerRequest req, JSONObject formData) throws FormException {
            BitbucketSCM scm = (BitbucketSCM) super.newInstance(req, formData);
            scm.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
            scm.setBitbucketPluginConfiguration(bitbucketPluginConfiguration);

            String serverId = formData.getString("serverId");
            if (isBlank(serverId)) {
                LOGGER.info("Error creating the Bitbucket SCM: The serverId cannot be blank");
                return scm;
            }
            Optional<BitbucketServerConfiguration> maybeServerConf = bitbucketPluginConfiguration.getServerById(serverId);
            if (!maybeServerConf.isPresent()) {
                LOGGER.info("Error creating the Bitbucket SCM: No server configuration for the given server id " + serverId);
                return scm;
            }
            BitbucketServerConfiguration serverConf = maybeServerConf.get();

            String credentialsId = formData.getString("credentialsId");
            BitbucketCredentials credentials = createWithFallback(CredentialUtils.getCredentials(credentialsId), serverConf);
            BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials);

            String projectName = formData.getString("projectName");
            String repositoryName = formData.getString("repositoryName");
                BitbucketProject project;
            if (isBlank(projectName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The projectName must not be blank");
                project = new BitbucketProject("", null, "");
            } else {
                try {
                    project = getProjectByNameOrKey(projectName, clientFactory);
                } catch (NotFoundException e) {
                    LOGGER.info("Error creating the Bitbucket SCM: Cannot find the project " + projectName);
                    project = new BitbucketProject(projectName, null, projectName);
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    LOGGER.info("Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " + e.getMessage());
                    project = new BitbucketProject(projectName, null, projectName);
                }
            }

            BitbucketRepository repository;
            if (isBlank(repositoryName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The repositoryName must not be blank");
                repository = new BitbucketRepository(repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
            } else {
                try {
                    repository = getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
                } catch (NotFoundException e) {
                    LOGGER.info("Error creating the Bitbucket SCM: Cannot find the repository " + projectName + "/" + repositoryName);
                    repository = new BitbucketRepository(repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    LOGGER.info("Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " + e.getMessage());
                    repository = new BitbucketRepository(repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
                }
            }

            scm.addRepositories(new BitbucketSCMRepository(credentialsId, projectName, project.getKey(), repositoryName, repository.getSlug(), serverId, false));
            scm.createGitSCM();
            return scm;
        }

        private BitbucketProject getProjectByNameOrKey(String projectNameOrKey, BitbucketClientFactory clientFactory) {
            return latestProjects.getValues().stream()
                    .filter(project -> projectNameOrKey.equalsIgnoreCase(project.getName()))
                    // There should only be one project with this key
                    .findAny()
                    // It wasn't in our cache so we need to call out to Bitbucket
                    .orElseGet(() -> clientFactory
                            .getSearchClient(projectNameOrKey)
                            .findProjects()
                            .getValues()
                            .stream()
                            .filter(p -> projectNameOrKey.equalsIgnoreCase(p.getName()))
                            // Project names are unique so there will only be one
                            .findAny()
                            // We didn't find the project so maybe they gave us a project key instead of name
                            .orElseGet(() -> clientFactory.getProjectClient(projectNameOrKey).getProject()));
        }

        private BitbucketRepository getRepositoryByNameOrSlug(String projectNameOrKey, String repositoryNameOrSlug,
                                                              BitbucketClientFactory clientFactory) {
            return latestRepositories.getValues().stream()
                    .filter(repository -> repositoryNameOrSlug.equalsIgnoreCase(repository.getName()))
                    // There should only be one repository with this name in the project
                    .findAny()
                    // It wasn't in our cache so we need to call out to Bitbucket
                    .orElseGet(() -> clientFactory.getSearchClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getName())
                            .findRepositories(repositoryNameOrSlug)
                            .getValues()
                            .stream()
                            .filter(r -> repositoryNameOrSlug.equalsIgnoreCase(r.getName()))
                            // Repo names are unique within a project
                            .findAny()
                            // Maybe the project and repo names they gave us are actually a key and slug
                            .orElseGet(() -> clientFactory
                                    .getProjectClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getKey())
                                    .getRepositoryClient(repositoryNameOrSlug)
                                    .getRepository()));
        }
    }
}
