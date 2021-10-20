package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentStepUtils.getOrGenerateEnvironmentKey;
import static com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentStepUtils.normalizeEnvironmentType;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

/**
 * Step for configuring deployment notifications.
 *
 * @since deployments
 */
public class DeploymentStepImpl extends Step implements DeploymentStep {

    private final String environmentName;

    private String environmentKey;
    private BitbucketDeploymentEnvironmentType environmentType;
    private String environmentUrl;

    @DataBoundConstructor
    public DeploymentStepImpl(@CheckForNull String environmentName) {
        this.environmentName = stripToNull(environmentName);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        Run<?, ?> run = context.get(Run.class);
        if (run == null) {
            throw new IllegalArgumentException("StepContext does not contain the Run");
        }
        TaskListener taskListener = context.get(TaskListener.class);
        if (taskListener == null) {
            throw new IllegalArgumentException("StepContext does not contain the TaskListener");
        }
        BitbucketDeploymentEnvironment environment = getEnvironment(run, taskListener);
        return new DeploymentStepExecution(environment, context);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BitbucketDeploymentEnvironment getEnvironment(Run<?, ?> run, TaskListener listener) {
        return DeploymentStepUtils.getEnvironment(this, run, listener);
    }

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

    @CheckForNull
    @Override
    public String getEnvironmentType() {
        return environmentType == null ? null : environmentType.name();
    }

    @DataBoundSetter
    public void setEnvironmentType(@CheckForNull String environmentType) {
        this.environmentType = normalizeEnvironmentType(environmentType);
    }

    @CheckForNull
    @Override
    public String getEnvironmentUrl() {
        return environmentUrl;
    }

    @DataBoundSetter
    public void setEnvironmentUrl(@CheckForNull String environmentUrl) {
        this.environmentUrl = stripToNull(environmentUrl);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @VisibleForTesting
        static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();

        @Inject
        private BitbucketDeploymentFactory bitbucketDeploymentFactory;
        @Inject
        private DeploymentPoster deploymentPoster;
        @Inject
        private JenkinsProvider jenkinsProvider;

        @POST
        public FormValidation doCheckEnvironmentName(@AncestorInPath @CheckForNull Item context,
                                                     @QueryParameter @CheckForNull String environmentName) {
            checkPermissions(context);
            if (isBlank(environmentName)) {
                return FormValidation.error(Messages.DeploymentNotifier_EnvironmentNameRequired());
            }
            return FORM_VALIDATION_OK;
        }

        @POST
        public FormValidation doCheckEnvironmentType(@AncestorInPath @CheckForNull Item context,
                                                     @QueryParameter @CheckForNull String environmentType) {
            checkPermissions(context);
            if (isBlank(environmentType)) {
                return FORM_VALIDATION_OK;
            }
            return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                    .map(validType -> FORM_VALIDATION_OK)
                    .orElseGet(() -> FormValidation.error(Messages.DeploymentNotifier_EnvironmentTypeInvalid()));
        }

        @POST
        public FormValidation doCheckEnvironmentUrl(@AncestorInPath @CheckForNull Item context,
                                                    @QueryParameter @CheckForNull String environmentUrl) {
            checkPermissions(context);
            if (isBlank(environmentUrl)) {
                return FORM_VALIDATION_OK;
            }
            try {
                URI uri = new URI(environmentUrl); // Try to coerce it into a URL
                if (!uri.isAbsolute()) {
                    return FormValidation.error(Messages.DeploymentNotifier_UriAbsolute());
                }
                return FORM_VALIDATION_OK;
            } catch (URISyntaxException e) {
                return FormValidation.error(Messages.DeploymentNotifier_EnvironmentUrlInvalid());
            }
        }

        @POST
        public ListBoxModel doFillEnvironmentTypeItems(@AncestorInPath @CheckForNull Item context) {
            checkPermissions(context);
            ListBoxModel options = new ListBoxModel();
            options.add(Messages.DeploymentNotifier_EmptySelection(), "");
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
            return "Wrapper step to notify Bitbucket Server of the deployment status.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(Run.class, Launcher.class, TaskListener.class));
        }

        @Override
        public String getFunctionName() {
            return "bbs_deploy";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
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
