package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.AuthorizationException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.ConnectionFailureException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsModule;
import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.inject.Guice;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static org.apache.commons.lang3.StringUtils.*;

@XStreamAlias("atl-bbs-configuration")
@SuppressWarnings("unused") // Stapler and UI stack calls method on this class via reflection
public class BitbucketServerConfiguration
        extends AbstractDescribableImpl<BitbucketServerConfiguration> {

    private static final Logger log = Logger.getLogger(BitbucketServerConfiguration.class.getName());

    private final String adminCredentialsId;
    private final String credentialsId;
    private final String id;
    private String baseUrl;
    private String serverName;

    @DataBoundConstructor
    public BitbucketServerConfiguration(
            String adminCredentialsId,
            String baseUrl,
            @Nullable String credentialsId,
            @Nullable String id) {
        this.adminCredentialsId = requireNonNull(adminCredentialsId);
        this.baseUrl = requireNonNull(baseUrl);
        this.credentialsId = credentialsId;
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
    }

    /**
     * For a given item, returns a global credential provider. The credentials are tracked and
     * this should be the only way to fetch credentials.
     *
     * @param item which will use the credential.
     * @return credential provider
     */
    public GlobalCredentialsProvider getGlobalCredentialsProvider(Item item) {
        return new GlobalCredentialsProvider() {
            @Override
            public Optional<BitbucketTokenCredentials> getGlobalAdminCredentials() {
                BitbucketTokenCredentials adminCredentials = BitbucketServerConfiguration.this.getAdminCredentials();
                return Optional.ofNullable(CredentialsProvider.track(item, adminCredentials));
            }

            @Override
            public Optional<Credentials> getGlobalCredentials() {
                Credentials adminCredentials = BitbucketServerConfiguration.this.getCredentials();
                return Optional.ofNullable(CredentialsProvider.track(item, adminCredentials));
            }
        };
    }

    /**
     * Similar to {@link BitbucketServerConfiguration#getGlobalCredentialsProvider(Item)} but without item. This
     * is usually a case when fetching credentials as part of `doFill` methods.
     *
     * @param context a context which will be logged
     * @return credential provider
     */
    public GlobalCredentialsProvider getGlobalCredentialsProvider(String context) {
        if (isBlank(context)) {
            throw new IllegalArgumentException("Please provide a valid non blank context");
        }
        return new GlobalCredentialsProvider() {
            @Override
            public Optional<BitbucketTokenCredentials> getGlobalAdminCredentials() {
                log.fine(format("Using admin credentials for [%s]", context));
                return Optional.ofNullable(BitbucketServerConfiguration.this.getAdminCredentials());
            }

            @Override
            public Optional<Credentials> getGlobalCredentials() {
                log.fine(format("Using global credentials for [%s]", context));
                return Optional.ofNullable(BitbucketServerConfiguration.this.getCredentials());
            }
        };
    }

    public String getAdminCredentialsId() {
        return adminCredentialsId;
    }

    /**
     * Returns the URL location of the server instance
     *
     * @return the bitbucket server base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the URL location of the server instance
     *
     * @param baseUrl the Bitbucket Server base URL
     */
    @DataBoundSetter
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = trimToEmpty(baseUrl);
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the name assigned to the server in Jenkins
     *
     * @return the server name
     */
    @Nullable
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the name assigned to the server in Jenkins
     *
     * @param serverName the server name
     */
    @DataBoundSetter
    public void setServerName(String serverName) {
        this.serverName = trimToEmpty(serverName);
    }

    /**
     * Checks that the configuration is valid
     *
     * @return true if valid; false otherwise
     */
    public FormValidation validate() {
        return FormValidation.aggregate(Arrays.asList(checkBaseUrl(baseUrl), checkServerName(serverName), checkAdminCredentialsId(adminCredentialsId)));
    }

    /**
     * Validates the provided admin credentials are present and appropriate
     *
     * @param adminCredentialsId the ID of the Bitbucket personal access token to check
     * @return FormValidation with Kind.ok if credentials are present and the correct type; Kind.error otherwise
     */
    private static FormValidation checkAdminCredentialsId(String adminCredentialsId) {
        if (isBlank(adminCredentialsId)) {
            return FormValidation.error("Choose a personal access token");
        }
        Credentials creds =
                firstOrNull(
                        lookupCredentials(
                                BitbucketTokenCredentials.class,
                                Jenkins.get(),
                                ACL.SYSTEM,
                                Collections.emptyList()),
                        withId(trimToEmpty(adminCredentialsId)));

        if (creds == null) {
            return FormValidation.error(
                    "We can't find that personal access token (has it been deleted?). Provide a different token and try again.");
        }
        return FormValidation.ok();
    }

    /**
     * Validates that the provided baseUrl value is syntactically valid
     *
     * @param baseUrl the URL to check
     * @return FormValidation with Kind.ok if syntactically valid; Kind.error otherwise
     */
    private static FormValidation checkBaseUrl(String baseUrl) {
        if (isEmpty(baseUrl)) {
            return FormValidation.error("Required");
        }
        try {
            URL base = new URL(baseUrl);
            if (isBlank(base.getHost())) {
                return FormValidation.error(
                        "This isn't a valid URL. Check for typos and make sure to include http:// or https://");
            } else if (base.getHost().contains("bitbucket.org")) {
                return FormValidation.error("This plugin does not support connecting to bitbucket.org. It is for Bitbucket Server instances only.");
            }
        } catch (MalformedURLException e) {
            return FormValidation.error(
                    "This isn't a valid URL. Check for typos and make sure to include http:// or https://");
        }
        return FormValidation.ok();
    }

    /**
     * Validates that the provided serverName is appropriate
     *
     * @param serverName the name to check
     * @return FormValidation with Kind.ok if valid; Kind.error otherwise
     */
    private static FormValidation checkServerName(String serverName) {
        return isBlank(serverName)
                ? FormValidation.error("Required")
                : FormValidation.ok();
    }

    @Nullable
    private BitbucketTokenCredentials getAdminCredentials() {
        return firstOrNull(
                lookupCredentials(
                        BitbucketTokenCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()),
                withId(trimToEmpty(adminCredentialsId)));
    }

    @Nullable
    private Credentials getCredentials() {
        return CredentialUtils.getCredentials(credentialsId);
    }

    @Symbol("BbS")
    @Extension
    public static class DescriptorImpl extends Descriptor<BitbucketServerConfiguration> {

        @Inject
        private BitbucketClientFactoryProvider clientFactoryProvider;
        private transient JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        @SuppressWarnings("MethodMayBeStatic")
        @POST
        public FormValidation doCheckAdminCredentialsId(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkAdminCredentialsId(value);
        }

        @SuppressWarnings("MethodMayBeStatic")
        @POST
        public FormValidation doCheckBaseUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkBaseUrl(value);
        }

        @SuppressWarnings("MethodMayBeStatic")
        @POST
        public FormValidation doCheckServerName(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkServerName(value);
        }

        @SuppressWarnings({"MethodMayBeStatic", "unused"})
        @POST
        public ListBoxModel doFillAdminCredentialsIdItems(
                @QueryParameter String baseUrl, @QueryParameter String credentialsId) {
            Jenkins instance = Jenkins.get();
            instance.checkPermission(Jenkins.ADMINISTER);

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            BitbucketTokenCredentials.class,
                            URIRequirementBuilder.fromUri(baseUrl).build(),
                            CredentialsMatchers.always());
        }

        @SuppressWarnings({"MethodMayBeStatic", "unused"})
        @POST
        public ListBoxModel doFillCredentialsIdItems(
                @QueryParameter String baseUrl, @QueryParameter String credentialsId) {
            Jenkins instance = Jenkins.get();
            instance.checkPermission(Jenkins.ADMINISTER);

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

        @SuppressWarnings("unused")
        @POST
        public FormValidation doTestConnection(
                @QueryParameter String adminCredentialsId,
                @QueryParameter String baseUrl,
                @QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            BitbucketServerConfiguration config =
                    new BitbucketServerConfiguration(
                            adminCredentialsId, baseUrl, credentialsId, null);
            Credentials credentials = config.getCredentials();
            if (credentials == null && isNotBlank(credentialsId)) {
                return FormValidation.error("We can't find these credentials. Provide different credentials and try again.");
            }
            if (config.getAdminCredentials() == null) {
                return FormValidation.error("A personal access token with project admin permissions is required.");
            }

            String context = "Test connection in global configuration";
            try {
                if (jenkinsToBitbucketCredentials == null) {
                    Guice.createInjector(new JenkinsToBitbucketCredentialsModule()).injectMembers(this);
                }
                Optional<String> username =
                        clientFactoryProvider
                                .getClient(
                                        config.getBaseUrl(),
                                        jenkinsToBitbucketCredentials.toBitbucketCredentials(config.getAdminCredentials(), config.getGlobalCredentialsProvider(context)))
                                .getAuthenticatedUserClient()
                                .getAuthenticatedUser();
                if (!username.isPresent()) {
                    return FormValidation.error("We can't connect to Bitbucket Server. Choose a different personal access token with project admin permissions");
                }
                BitbucketClientFactory client =
                        clientFactoryProvider.getClient(
                                config.getBaseUrl(),
                                jenkinsToBitbucketCredentials.toBitbucketCredentials(credentials, config.getGlobalCredentialsProvider(context)));

                AtlassianServerCapabilities capabilities = client.getCapabilityClient().getServerCapabilities();
                if (credentials instanceof StringCredentials) {
                    if (!client.getAuthenticatedUserClient().getAuthenticatedUser().isPresent()) {
                        throw new AuthorizationException("TO WRITE", HTTP_UNAUTHORIZED, null);
                    }
                }

                if (capabilities.isBitbucketServer()) {
                    return FormValidation.ok("Jenkins can connect with Bitbucket Server.");
                }
                return FormValidation.error("This is not the URL of a Bitbucket Server instance. Enter a different URL and try again.");
            } catch (ConnectionFailureException e) {
                return FormValidation.error(
                        "Could not connect to remote server, please ensure url is correct and server is running");
            } catch (NotFoundException e) {
                return FormValidation.error(
                        "This is not the URL of a Bitbucket Server instance. Enter a different URL and try again.");
            } catch (AuthorizationException e) {
                return FormValidation.error("Jenkins can't connect to Bitbucket Server. Choose different credentials or choose 'none'.");
            } catch (BitbucketClientException e) {
                Logger.getLogger(DescriptorImpl.class.getName()).log(FINE, "Failed to connect to Bitbucket server", e);
                return FormValidation.error("Connection failure, please try again");
            }
        }

        @Override
        public String getDisplayName() {
            return "Instance details";
        }

        @Inject
        public void setJenkinsToBitbucketCredentials(
                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
            this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        }
    }
}
