package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentStepUtils.getOrGenerateEnvironmentKey;
import static com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentStepUtils.normalizeEnvironmentType;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripToNull;

/**
 * Step for configuring deployment notification in freestyle jobs.
 *
 * @since 3.1.0
 */
public class DeploymentNotifier extends Notifier implements SimpleBuildStep, DeploymentStep {

    private static final Logger LOGGER = Logger.getLogger(DeploymentNotifier.class.getName());

    private final String environmentName;

    private String environmentKey;
    private BitbucketDeploymentEnvironmentType environmentType;
    private String environmentUrl;

    @DataBoundConstructor
    public DeploymentNotifier(@CheckForNull String environmentName) {
        // We need to create this now because the environment key is required and if setEnvironmentKey is never called
        // we need an environment key that remains stable.
        this.environmentKey = getOrGenerateEnvironmentKey();
        this.environmentName = stripToNull(environmentName);
    }

    public DescriptorImpl descriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Used to populate the {@code environmentKey} field in the UI
     *
     * @return the configured or generated environment key
     */
    @Override
    public String getEnvironmentKey() {
        return environmentKey;
    }

    @DataBoundSetter
    public void setEnvironmentKey(@CheckForNull String environmentKey) {
        this.environmentKey = getOrGenerateEnvironmentKey(environmentKey);
    }

    @CheckForNull
    @Override
    public String getEnvironmentName() {
        return environmentName;
    }

    /**
     * Used to populate the {@code environmentType} field in the UI
     *
     * @return the configured {@link BitbucketDeploymentEnvironmentType#name()} or {@code null} if not configured
     */
    @CheckForNull
    @Override
    public String getEnvironmentType() {
        return environmentType == null ? null : environmentType.name();
    }

    @DataBoundSetter
    public void setEnvironmentType(@CheckForNull String environmentType) {
        this.environmentType = normalizeEnvironmentType(environmentType);
    }

    /**
     * Used to populate the {@code environmentUrl} field in the UI
     *
     * @return the configured environment url or {@code null} if not configured
     */
    @CheckForNull
    @Override
    public String getEnvironmentUrl() {
        return environmentUrl;
    }

    @DataBoundSetter
    public void setEnvironmentUrl(@CheckForNull String environmentUrl) {
        this.environmentUrl = stripToNull(environmentUrl);
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
                        TaskListener listener) throws InterruptedException, IOException {
        try {
            BitbucketRevisionAction revisionAction = run.getAction(BitbucketRevisionAction.class);
            if (revisionAction == null) {
                // Not checked out with a Bitbucket SCM
                listener.error("Could not send deployment notification: DeploymentNotifier only works when using the Bitbucket SCM for checkout.");
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

    @Override
    public BitbucketDeploymentEnvironment getEnvironment(Run<?, ?> run, TaskListener listener) {
        return DeploymentStepUtils.getEnvironment(this, run, listener);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();

        @Inject
        private BitbucketDeploymentFactory bitbucketDeploymentFactory;
        @Inject
        private DeploymentPoster deploymentPoster;
        @Inject
        private DeploymentStepDescriptorHelper descriptorHelper;

        @POST
        public FormValidation doCheckEnvironmentKey(@AncestorInPath @CheckForNull Item context,
                                                    @QueryParameter @CheckForNull String environmentKey) {
            return descriptorHelper.doCheckEnvironmentKey(context, environmentKey);
        }

        @POST
        public FormValidation doCheckEnvironmentName(@AncestorInPath Item context,
                                                     @QueryParameter String environmentName) {
            return descriptorHelper.doCheckEnvironmentName(context, environmentName);
        }

        @POST
        public FormValidation doCheckEnvironmentType(@AncestorInPath Item context,
                                                     @QueryParameter String environmentType) {
            return descriptorHelper.doCheckEnvironmentType(context, environmentType);
        }

        @POST
        public FormValidation doCheckEnvironmentUrl(@AncestorInPath Item context,
                                                    @QueryParameter String environmentUrl) {
            return descriptorHelper.doCheckEnvironmentUrl(context, environmentUrl);
        }

        @POST
        public ListBoxModel doFillEnvironmentTypeItems(@AncestorInPath Item context) {
            return descriptorHelper.doFillEnvironmentTypeItems(context);
        }

        public BitbucketDeploymentFactory getBitbucketDeploymentFactory() {
            return bitbucketDeploymentFactory;
        }

        public DeploymentPoster getDeploymentPoster() {
            return deploymentPoster;
        }

        @Override
        public String getDisplayName() {
            return Messages.DeploymentNotifier_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
