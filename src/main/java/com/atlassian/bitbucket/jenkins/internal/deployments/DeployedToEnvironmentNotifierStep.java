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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class DeployedToEnvironmentNotifierStep extends Notifier implements SimpleBuildStep {

    private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();
    private static final Logger LOGGER = Logger.getLogger(DeployedToEnvironmentNotifierStep.class.getName());

    private final BitbucketDeploymentEnvironment environment;

    @DataBoundConstructor
    public DeployedToEnvironmentNotifierStep(String environmentKey, String environmentName,
                                             @CheckForNull String environmentType,
                                             @CheckForNull String environmentUrl) {
        environment = validateAndGetEnvironment(environmentKey, environmentName, environmentType, environmentUrl);
    }

    public DescriptorImpl descriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    public BitbucketDeploymentEnvironment getEnvironment() {
        return environment;
    }

    public String getEnvironmentKey() {
        return environment.getKey();
    }

    public String getEnvironmentName() {
        return environment.getName();
    }

    @CheckForNull
    public String getEnvironmentType() {
        return environment.getType();
    }

    @CheckForNull
    public String getEnvironmentUrl() {
        return environment.getUrl();
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
            BitbucketDeployment deployment =
                    descriptor().getBitbucketDeploymentFactory().createDeployment(run, environment);

            BitbucketSCMRepository bitbucketSCMRepo = revisionAction.getBitbucketSCMRepo();
            String revisionSha = revisionAction.getRevisionSha1();
            descriptor().getDeploymentPoster().postDeployment(bitbucketSCMRepo.getServerId(),
                    bitbucketSCMRepo.getProjectKey(), bitbucketSCMRepo.getRepositorySlug(), revisionSha, deployment,
                    run, listener);
        } catch (RuntimeException e) {
            // This shouldn't happen because deploymentPoster.postDeployment doesn't throw anything. But just in case,
            // we don't want to throw anything and potentially stop other steps from being executed
            String errorMsg =
                    String.format("An exception occurred when trying to post the deployment to Bitbucket Server: %s", e.getMessage());
            listener.error(errorMsg);
            LOGGER.info(errorMsg);
            LOGGER.log(Level.FINE, "Stacktrace from deployment post failure", e);
        }
    }

    private static String getOrGenerateEnvironmentKey(@CheckForNull String environmentKey) {
        if (!isBlank(environmentKey)) {
            return environmentKey;
        }
        return UUID.randomUUID().toString();
    }

    private static BitbucketDeploymentEnvironment validateAndGetEnvironment(String environmentKey,
                                                                            String environmentName,
                                                                            @CheckForNull String environmentType,
                                                                            @CheckForNull String environmentUrl) {
        FormValidation environmentNameValidation = validateEnvironmentName(environmentName);
        if (!environmentNameValidation.equals(FORM_VALIDATION_OK)) {
            throw new IllegalArgumentException(environmentNameValidation.getMessage());
        }
        FormValidation environmentTypeValidation = validateEnvironmentType(environmentType);
        if (!environmentTypeValidation.equals(FORM_VALIDATION_OK)) {
            throw new IllegalArgumentException(environmentTypeValidation.getMessage());
        }
        FormValidation environmentUrlValidation = validateEnvironmentUrl(environmentUrl);
        if (!environmentUrlValidation.equals(FORM_VALIDATION_OK)) {
            throw new IllegalArgumentException(environmentUrlValidation.getMessage());
        }
        return new BitbucketDeploymentEnvironment.Builder(getOrGenerateEnvironmentKey(environmentKey),
                environmentName)
                .type(BitbucketDeploymentEnvironmentType.fromName(stripToNull(environmentType)).orElse(null))
                .url(environmentUrl)
                .build();
    }

    private static FormValidation validateEnvironmentName(@CheckForNull String environmentName) {
        if (isBlank(environmentName)) {
            return FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentNameRequired());
        }
        return FORM_VALIDATION_OK;
    }

    private static FormValidation validateEnvironmentType(@CheckForNull String environmentType) {
        if (isBlank(environmentType)) {
            return FORM_VALIDATION_OK;
        }
        return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                .map(validType -> FORM_VALIDATION_OK)
                .orElseGet(() -> FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentTypeInvalid()));
    }

    private static FormValidation validateEnvironmentUrl(@CheckForNull String environmentUrl) {
        if (isBlank(environmentUrl)) {
            return FORM_VALIDATION_OK;
        }
        try {
            new URI(environmentUrl); // Try to coerce it into a URL
            return FORM_VALIDATION_OK;
        } catch (URISyntaxException e) {
            return FormValidation.error(Messages.DeployedToEnvironmentNotifierStep_EnvironmentUrlInvalid());
        }
    }

    @Extension
    @Symbol("DeployedToEnvironmentNotifierStep")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

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
            return validateEnvironmentName(environmentName);
        }

        @POST
        public FormValidation doCheckEnvironmentType(@AncestorInPath Item context,
                                                     @QueryParameter String environmentType) {
            checkPermissions(context);
            return validateEnvironmentType(environmentType);
        }

        @POST
        public FormValidation doCheckEnvironmentUrl(@AncestorInPath Item context,
                                                    @QueryParameter String environmentUrl) {
            checkPermissions(context);
            return validateEnvironmentUrl(environmentUrl);
        }

        @POST
        public ListBoxModel doFillEnvironmentTypeItems(@AncestorInPath Item context) {
            checkPermissions(context);
            ListBoxModel options = new ListBoxModel();
            options.add(Messages.DeployedToEnvironmentNotifierStep_EmptySelection(), null);
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
