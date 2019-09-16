package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
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

        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        if (isBlank(serverId)) {
            throw new BitbucketSCMException("A server is required", "serverId");
        }

        repositories = new ArrayList<>(1);
        this.gitTool = gitTool;
        this.branches = branches;
        this.extensions = new ArrayList<>();
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
        this.extensions.add(new BitbucketPostBuildStatus(serverId));
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

        try {
            String credentialsId = scmRepository.getCredentialsId();
            BitbucketRepository repo =
                    getRepository(
                            server, scmRepository.getProjectKey(), repositorySlug, credentialsId);
            UserRemoteConfig remoteConfig =
                    new UserRemoteConfig(
                            getCloneUrl(repo),
                            repositorySlug,
                            null,
                            pickCredentialsId(server, credentialsId));
            gitSCM =
                    new GitSCM(
                            Collections.singletonList(remoteConfig),
                            branches,
                            false,
                            Collections.emptyList(),
                            new Stash(getRepositoryUrl(repo)),
                            gitTool,
                            extensions);
        } catch (BitbucketClientException e) {
            throw new BitbucketSCMException(
                    "Failed to save configuration. Reason: " + firstNonBlank(e.getMessage(), "unknown") +
                    ". Please use the back button on your browser and try again.");
        }
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
        String selfLink =
                repository.getSelfLink(); // self-link include /browse which needs to be trimmed
        return selfLink.substring(0, selfLink.length() - "/browse".length());
    }

    private BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
    }

    private BitbucketRepository getRepository(
            BitbucketServerConfiguration server,
            String projectKey,
            String repositorySlug,
            String credentialsId) {
        return bitbucketClientFactoryProvider
                .getClient(server.getBaseUrl(), createWithFallback(credentialsId, server))
                .getProjectClient(projectKey)
                .getRepositoryClient(repositorySlug)
                .get();
    }

    private BitbucketServerConfiguration getServer() {
        return bitbucketPluginConfiguration
                .getServerById(getBitbucketSCMRepository().getServerId())
                .orElseThrow(() -> new RuntimeException("Server config not found"));
    }

    @Nullable
    private String pickCredentialsId(
            BitbucketServerConfiguration serverConfiguration, @Nullable String credentialsId) {
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
                            BitbucketProjectSearchClient projectSearchClient = bitbucketClientFactoryProvider
                                    .getClient(serverConf.getBaseUrl(), credentials)
                                    .getProjectSearchClient();
                            latestProjects = projectSearchClient.get(stripToEmpty(projectName));
                            return okJSON(JSONObject.fromObject(latestProjects));
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            LOGGER.severe(e.getMessage());
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
                        BitbucketRepositorySearchClient searchClient = bitbucketClientFactoryProvider
                                .getClient(serverConf.getBaseUrl(), credentials)
                                .getRepositorySearchClient(projectName);
                        try {
                            latestRepositories = searchClient.get(stripToEmpty(repositoryName));
                            return okJSON(JSONObject.fromObject(latestRepositories));
                        } catch (BitbucketClientException e) {
                            // Something went wrong with the request to Bitbucket
                            LOGGER.severe(e.getMessage());
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
        public SCM newInstance(@Nullable StaplerRequest req, JSONObject formData)
                throws FormException {
            try {
                BitbucketSCM scm = (BitbucketSCM) super.newInstance(req, formData);
                scm.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
                scm.setBitbucketPluginConfiguration(bitbucketPluginConfiguration);

                String serverId = formData.getString("serverId");
                BitbucketServerConfiguration serverConf = bitbucketPluginConfiguration.getServerById(serverId)
                        .orElseThrow(() -> new FormException("No server with ID " + serverId, "serverId"));

                String credentialsId = formData.getString("credentialsId");
                BitbucketCredentials credentials = createWithFallback(CredentialUtils.getCredentials(credentialsId), serverConf);
                BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials);

                String projectName = formData.getString("projectName");
                if (isBlank(projectName)) {
                    throw new BitbucketSCMException("A project is required", "projectName");
                }
                BitbucketProject project;
                try {
                    project = getProjectByNameOrKey(projectName, clientFactory);
                } catch (NotFoundException e) {
                    throw new FormException("Cannot find the project " + projectName, "projectName");
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    throw new FormException("Something went wrong when trying to contact Bitbucket Server: " + e.getMessage(), "projectName");
                }

                String repositoryName = formData.getString("repositoryName");
                if (isBlank(repositoryName)) {
                    throw new BitbucketSCMException("A repository is required", "repositoryName");
                }
                BitbucketRepository repository;
                try {
                    repository = getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
                } catch (NotFoundException e) {
                    throw new FormException("Cannot find the repository " + projectName + "/" + repositoryName, "repositoryName");
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    throw new FormException("Something went wrong when trying to contact Bitbucket Server: " + e.getMessage(), "repositoryName");
                }

                scm.addRepositories(new BitbucketSCMRepository(credentialsId, projectName, project.getKey(), repositoryName, repository.getSlug(), serverId, false));
                scm.createGitSCM();
                return scm;
            } catch (Error | RuntimeException e) {
                Throwable cause = e;
                do {
                    if (cause instanceof BitbucketSCMException) {
                        throw new FormException(
                                cause.getMessage(), ((BitbucketSCMException) cause).getField());
                    }
                    cause = cause.getCause();
                } while (cause != null);
                throw e; // didn't match any known error, so throw it up and let Jenkins handle it
            }
        }

        private BitbucketProject getProjectByNameOrKey(String projectNameOrKey, BitbucketClientFactory clientFactory) {
            return latestProjects.getValues().stream()
                    .filter(project -> projectNameOrKey.equalsIgnoreCase(project.getName()))
                    // There should only be one project with this key
                    .findAny()
                    // It wasn't in our cache so we need to call out to Bitbucket
                    .orElseGet(() -> clientFactory
                            .getProjectSearchClient()
                            .get(projectNameOrKey)
                            .getValues()
                            .stream()
                            .filter(p -> projectNameOrKey.equalsIgnoreCase(p.getName()))
                            // Project names are unique so there will only be one
                            .findAny()
                            // We didn't find the project so maybe they gave us a project key instead of name
                            .orElseGet(() -> clientFactory.getProjectClient(projectNameOrKey).get()));
        }

        private BitbucketRepository getRepositoryByNameOrSlug(String projectNameOrKey, String repositoryNameOrSlug,
                                                              BitbucketClientFactory clientFactory) {
            return latestRepositories.getValues().stream()
                    .filter(repository -> repositoryNameOrSlug.equalsIgnoreCase(repository.getName()))
                    // There should only be one repository with this name in the project
                    .findAny()
                    // It wasn't in our cache so we need to call out to Bitbucket
                    .orElseGet(() -> clientFactory.getRepositorySearchClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getName())
                            .get(repositoryNameOrSlug)
                            .getValues()
                            .stream()
                            .filter(r -> repositoryNameOrSlug.equalsIgnoreCase(r.getName()))
                            // Repo names are unique within a project
                            .findAny()
                            // Maybe the project and repo names they gave us are actually a key and slug
                            .orElseGet(() -> clientFactory
                                    .getProjectClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getKey())
                                    .getRepositoryClient(repositoryNameOrSlug)
                                    .get()));
        }
    }
}
