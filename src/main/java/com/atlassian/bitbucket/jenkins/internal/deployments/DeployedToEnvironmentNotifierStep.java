package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

/**
 * Step for configuring deployment notification in freestyle jobs.
 *
 * @since deployments
 */
public class DeployedToEnvironmentNotifierStep extends Notifier implements SimpleBuildStep {

    private static final Logger LOGGER = Logger.getLogger(DeployedToEnvironmentNotifierStep.class.getName());

    private final String environmentKey;
    private final String environmentName;
    private final BitbucketDeploymentEnvironmentType environmentType;
    private final String environmentUrl;

    @DataBoundConstructor
    public DeployedToEnvironmentNotifierStep(@CheckForNull String environmentKey,
                                             @CheckForNull String environmentName,
                                             @CheckForNull String environmentType,
                                             @CheckForNull String environmentUrl) {
        this.environmentKey = getOrGenerateEnvironmentKey(environmentKey);
        this.environmentName = stripToNull(environmentName);
        this.environmentType = getEnvironmentType(environmentType);
        this.environmentUrl = stripToNull(environmentUrl);
    }

    public DescriptorImpl descriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Used to populate the {@code environmentKey} field in the UI
     *
     * @return the configured or generated environment key
     */
    public String getEnvironmentKey() {
        return environmentKey;
    }

    /**
     * Used to populate the {@code environmentName} field in the UI
     *
     * @return the configured environment name or {@code null} if not configured
     */
    @CheckForNull
    public String getEnvironmentName() {
        return environmentName;
    }

    /**
     * Used to populate the {@code environmentType} field in the UI
     *
     * @return the configured {@link BitbucketDeploymentEnvironmentType#name()} or {@code null} if not configured
     */
    @CheckForNull
    public String getEnvironmentType() {
        return environmentType == null ? null : environmentType.name();
    }

    /**
     * Used to populate the {@code environmentUrl} field in the UI
     *
     * @return the configured environment url or {@code null} if not configured
     */
    @CheckForNull
    public String getEnvironmentUrl() {
        return environmentUrl;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
                        TaskListener listener) throws InterruptedException, IOException {
        try {
            BitbucketRevisionAction revisionAction = run.getAction(BitbucketRevisionAction.class);
            if (revisionAction == null) {
                // Not checked out with a Bitbucket SCM
                listener.error("Could not send deployment notification: DeployedToEnvironmentNotifierStep only works when using the Bitbucket SCM for checkout.");
                return;
            }

            // We use the default call to the bitbucketDeploymentFactory to get the state of the deployment based
            // on the run.
            BitbucketDeploymentEnvironment environment = getEnvironment(run, listener);
            BitbucketDeployment deployment = descriptor()
                    .getBitbucketDeploymentFactory()
                    .createDeployment(run, environment);

            BitbucketSCMRepository bitbucketSCMRepo = revisionAction.getBitbucketSCMRepo();
            String revisionSha = revisionAction.getRevisionSha1();
            descriptor().getDeploymentPoster().postDeployment(bitbucketSCMRepo, revisionSha, deployment, run, listener);
        } catch (RuntimeException e) {
            // This shouldn't happen because deploymentPoster.postDeployment doesn't throw anything. But just in case,
            // we don't want to throw anything and potentially stop other steps from being executed
            String errorMsg =
                    format("An error occurred when trying to post the deployment to Bitbucket Server: %s", e.getMessage());
            listener.error(errorMsg);
            LOGGER.info(errorMsg);
            LOGGER.log(Level.FINE, "Stacktrace from deployment post failure", e);
        }
    }

    public BitbucketDeploymentEnvironment getEnvironment(Run<?, ?> run, TaskListener listener) {
        return new BitbucketDeploymentEnvironment(environmentKey,
                getOrGenerateEnvironmentName(environmentName, run, listener),
                environmentType,
                getEnvironmentUri(environmentUrl, listener));
    }

    @CheckForNull
    private BitbucketDeploymentEnvironmentType getEnvironmentType(@CheckForNull String environmentType) {
        if (isBlank(environmentType)) {
            return null;
        }
        return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                .orElseGet(() -> {
                    LOGGER.warning(format("DeployedToEnvironmentNotifierStep: Invalid environment type '%s'. Saving step without environment type.", environmentType));
                    return null;
                });
    }

    @CheckForNull
    private URI getEnvironmentUri(String environmentUrl, TaskListener listener) {
        if (isBlank(environmentUrl)) {
            return null;
        }
        try {
            return new URI(environmentUrl);
        } catch (URISyntaxException x) {
            listener.getLogger().println(format("DeployedToEnvironmentNotifierStep: Invalid environment URL '%s'. Posting deployment without a URL instead.", this.environmentUrl));
            return null;
        }
    }

    private String getOrGenerateEnvironmentKey(@CheckForNull String environmentKey) {
        if (!isBlank(environmentKey)) {
            return environmentKey;
        }
        return UUID.randomUUID().toString();
    }

    private String getOrGenerateEnvironmentName(@CheckForNull String environmentName, Run<?, ?> run,
                                                TaskListener listener) {
        if (!isBlank(environmentName)) {
            return environmentName;
        }
        String generatedEnvironmentName;
        if (environmentType != null) {
            // Default to the environment type display name if there is a configured environment type
            generatedEnvironmentName = environmentType.getDisplayName();
        } else {
            // Otherwise default to the project's display name
            generatedEnvironmentName = run.getParent().getDisplayName();
        }
        listener.getLogger().println(format("Bitbucket Deployment Notifier: Using '%s' as the environment name since it was not correctly configured. Please configure an environment name.", generatedEnvironmentName));
        return generatedEnvironmentName;
    }

    @Extension
    @Symbol("DeployedToEnvironmentNotifierStep")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();

        @Inject
        private BitbucketDeploymentFactory bitbucketDeploymentFactory;
        @Inject
        private DeploymentPoster deploymentPoster;
        @Inject
        private JenkinsProvider jenkinsProvider;

        @POST
        public FormValidation doCheckEnvironmentName(@AncestorInPath Item context,
                                                     @QueryParameter String environmentName) {
            checkPermissions(context);
            if (isBlank(environmentName)) {
                return FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentNameRequired());
            }
            return FORM_VALIDATION_OK;
        }

        @POST
        public FormValidation doCheckEnvironmentType(@AncestorInPath Item context,
                                                     @QueryParameter String environmentType) {
            checkPermissions(context);
            if (isBlank(environmentType)) {
                return FORM_VALIDATION_OK;
            }
            return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                    .map(validType -> FORM_VALIDATION_OK)
                    .orElseGet(() -> FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentTypeInvalid()));
        }

        @POST
        public FormValidation doCheckEnvironmentUrl(@AncestorInPath Item context,
                                                    @QueryParameter String environmentUrl) {
            checkPermissions(context);
            if (isBlank(environmentUrl)) {
                return FORM_VALIDATION_OK;
            }
            try {
                URI uri = new URI(environmentUrl); // Try to coerce it into a URL
                if (!uri.isAbsolute()) {
                    return FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_UriAbsolute());
                }
                return FORM_VALIDATION_OK;
            } catch (URISyntaxException e) {
                return FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentUrlInvalid());
            }
        }

        @POST
        public ListBoxModel doFillEnvironmentTypeItems(@AncestorInPath Item context) {
            checkPermissions(context);
            ListBoxModel options = new ListBoxModel();
            options.add(Messages.DeployedToEnvironmentNotifierStep_EmptySelection(), "");
            Arrays.stream(BitbucketDeploymentEnvironmentType.values())
                    .sorted(Comparator.comparingInt(BitbucketDeploymentEnvironmentType::getWeight))
                    .forEach(v -> options.add(v.getDisplayName(), v.name()));
            return options;
        }

        public BitbucketDeploymentFactory getBitbucketDeploymentFactory() {
            return bitbucketDeploymentFactory;
        }

        public DeploymentPoster getDeploymentPoster() {
            return deploymentPoster;
        }

        @Override
        public String getDisplayName() {
            return Messages.DeployedToEnvironmentNotifierStep_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        private void checkPermissions(@CheckForNull Item context) {
            if (context != null) {
                context.checkPermission(Item.EXTENDED_READ);
            } else {
                jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            }
        }
    }
}
