package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.CheckForNull;
import java.io.PrintStream;
import java.net.URI;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentStepImpl.DescriptorImpl.FORM_VALIDATION_OK;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

@SuppressWarnings("ThrowableNotThrown") // calling doCheck methods on the descriptor cause this warning
@RunWith(MockitoJUnitRunner.class)
public class DeploymentStepImplTest {

    private static final Logger log = Logger.getLogger(DeploymentStepImplTest.class.getName());

    @Mock
    private Run<FreeStyleProject, ?> run;
    @InjectMocks
    private DeploymentStepImpl.DescriptorImpl descriptor;
    @Mock
    private StepContext context;
    @Mock
    private TaskListener taskListener;
    @Mock
    private PrintStream printStream;
    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Jenkins jenkins;

    private DeploymentStepImpl deploymentStep;

    @Before
    public void setup() throws Exception {
        createDeploymentStep(null, null, null, null);
        when(context.get(Run.class)).thenReturn(run);
        when(context.get(TaskListener.class)).thenReturn(taskListener);
        when(taskListener.getLogger()).thenReturn(printStream);
        when(jenkinsProvider.get()).thenReturn(jenkins);
    }

    @Test
    public void testDescriptorDoCheckEnvironmentNameContextNull() {
        String environmentName = "my env";

        FormValidation formValidation = descriptor.doCheckEnvironmentName(null, environmentName);

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentName() {
        Item context = mock(Item.class);
        String environmentName = "my env";

        FormValidation formValidation = descriptor.doCheckEnvironmentName(context, environmentName);

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentNameBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentName(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment name is required."));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentTypeContextNull() {
        FormValidation formValidation = descriptor.doCheckEnvironmentType(null, "Production");

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentTypeBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentType(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentType() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentType(context, "PRODUCTION");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentTypeBadEnvironmentType() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentType(context, "not an environment type");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment type should be one of DEVELOPMENT, PRODUCTION, STAGING, TESTING."));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrlContextNull() {
        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(null, "http://my-env");

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrlBlank() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(context, " ");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrlInvalid() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(context, "not a url");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The environment URL must be a valid URL."));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrlNotAbsolute() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(context, "/relative/url");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation.getMessage(), equalTo("The deployment URI must be absolute."));
    }

    @Test
    public void testDoFillEnvironmentTypeItems() {
        ListBoxModel options = descriptor.doFillEnvironmentTypeItems(null);

        verify(jenkins).checkPermission(Jenkins.ADMINISTER);
        assertThat(options, hasSize(5));
        assertThat(options.get(0).name, equalTo("- none -"));
        assertThat(options.get(0).value, equalTo(""));
        assertThat(options.get(1).name, equalTo("Production"));
        assertThat(options.get(1).value, equalTo("PRODUCTION"));
        assertThat(options.get(2).name, equalTo("Staging"));
        assertThat(options.get(2).value, equalTo("STAGING"));
        assertThat(options.get(3).name, equalTo("Testing"));
        assertThat(options.get(3).value, equalTo("TESTING"));
        assertThat(options.get(4).name, equalTo("Development"));
        assertThat(options.get(4).value, equalTo("DEVELOPMENT"));
    }

    @Test
    public void testDescriptorDoCheckEnvironmentUrl() {
        Item context = mock(Item.class);

        FormValidation formValidation = descriptor.doCheckEnvironmentUrl(context, "http://my-env");

        verify(context).checkPermission(Item.EXTENDED_READ);
        verifyZeroInteractions(jenkins);
        assertThat(formValidation, equalTo(FORM_VALIDATION_OK));
    }

    @Test
    public void testStartWhenNoRun() throws Exception {
        when(context.get(Run.class)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> deploymentStep.start(context));

        assertThat(exception.getMessage(), equalTo("StepContext does not contain the Run"));
    }

    @Test
    public void testStartWhenNoTaskListener() throws Exception {
        when(context.get(TaskListener.class)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> deploymentStep.start(context));

        assertThat(exception.getMessage(), equalTo("StepContext does not contain the TaskListener"));
    }

    @Test
    public void testStart() throws Exception {
        String environmentName = "My env";
        String environmentKey = "ENV-123";
        String environmentType = "Production";
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(environmentName, environmentKey, environmentType, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), equalTo(getEnvironmentType(environmentType)));
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
    }

    @Test
    public void testStartEnvironmentTypeNull() throws Exception {
        String environmentName = "My env";
        String environmentKey = "ENV-123";
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(environmentName, environmentKey, null, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), nullValue());
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
    }

    @Test
    public void testStartEnvironmentTypeBlank() throws Exception {
        String environmentName = "My env";
        String environmentKey = "ENV-123";
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(environmentName, environmentKey, " ", environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), nullValue());
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
    }

    @Test
    public void testStartEnvironmentTypeInvalid() throws Exception {
        String environmentName = "My env";
        String environmentKey = "ENV-123";
        String environmentType = "not a real environment type";
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(environmentName, environmentKey, environmentType, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), nullValue());
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
    }

    @Test
    public void testStartNameNull() throws Exception {
        String environmentKey = "ENV-123";
        String environmentType = "Production";
        BitbucketDeploymentEnvironmentType expectedEnvironmentType = getEnvironmentType(environmentType);
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(null, environmentKey, environmentType, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentType));
        assertThat(environment.getType(), equalTo(expectedEnvironmentType));
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
        verify(printStream).println(format("Using '%s' as the environment name since it was not correctly configured. Please configure an environment name.", expectedEnvironmentType.getDisplayName()));
    }

    @Test
    public void testStartNameBlank() throws Exception {
        String environmentKey = "ENV-123";
        String environmentType = "Production";
        BitbucketDeploymentEnvironmentType expectedEnvironmentType = getEnvironmentType(environmentType);
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(" ", environmentKey, environmentType, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentType));
        assertThat(environment.getType(), equalTo(expectedEnvironmentType));
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
        verify(printStream).println(format("Using '%s' as the environment name since it was not correctly configured. Please configure an environment name.", expectedEnvironmentType.getDisplayName()));
    }

    @Test
    public void testStartNameBlankEnvironmentTypeBlank() throws Exception {
        String environmentKey = "ENV-123";
        String environmentUrl = "http://localhost:8080";
        createDeploymentStep(" ", environmentKey, null, environmentUrl);
        FreeStyleProject parent = mock(FreeStyleProject.class);
        when(run.getParent()).thenReturn(parent);
        String parentDisplayName = "Parent name";
        when(parent.getDisplayName()).thenReturn(parentDisplayName);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(parentDisplayName));
        assertThat(environment.getType(), nullValue());
        assertThat(environment.getUrl(), equalTo(URI.create(environmentUrl)));
        verify(printStream).println(format("Using '%s' as the environment name since it was not correctly configured. Please configure an environment name.", parentDisplayName));
    }

    @Test
    public void testStartEnvironmentUriBlank() throws Exception {
        String environmentKey = "ENV-123";
        String environmentName = "My Env";
        String environmentType = "Production";
        createDeploymentStep(environmentName, environmentKey, environmentType, " ");

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), equalTo(getEnvironmentType(environmentType)));
        assertThat(environment.getUrl(), nullValue());
        verifyZeroInteractions(printStream);
    }

    @Test
    public void testStartEnvironmentUriNull() throws Exception {
        String environmentKey = "ENV-123";
        String environmentName = "My Env";
        String environmentType = "Production";
        createDeploymentStep(environmentName, environmentKey, environmentType, null);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), equalTo(getEnvironmentType(environmentType)));
        assertThat(environment.getUrl(), nullValue());
        verifyZeroInteractions(printStream);
    }

    @Test
    public void testStartEnvironmentUriInvalid() throws Exception {
        String environmentKey = "ENV-123";
        String environmentName = "My Env";
        String environmentType = "Production";
        String environmentUrl = "not a url";
        createDeploymentStep(environmentName, environmentKey, environmentType, environmentUrl);

        StepExecution stepExecution = deploymentStep.start(context);

        assertThat(stepExecution, instanceOf(DeploymentStepExecution.class));
        BitbucketDeploymentEnvironment environment = ((DeploymentStepExecution) stepExecution).getEnvironment();
        assertThat(environment.getKey(), equalTo(environmentKey));
        assertThat(environment.getName(), equalTo(environmentName));
        assertThat(environment.getType(), equalTo(getEnvironmentType(environmentType)));
        assertThat(environment.getUrl(), nullValue());
        verify(printStream).println(format("Invalid environment URL '%s'.", environmentUrl));
    }

    private BitbucketDeploymentEnvironmentType getEnvironmentType(String environmentType) {
        return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                .orElseThrow(() -> new IllegalArgumentException("Invalid environment type " + environmentType));
    }

    private void createDeploymentStep(String environmentName, @CheckForNull String environmentKey,
                                      @CheckForNull String environmentType, @CheckForNull String environmentUrl) {
        deploymentStep = new DeploymentStepImpl(environmentName);
        deploymentStep.setEnvironmentKey(environmentKey);
        deploymentStep.setEnvironmentType(environmentType);
        deploymentStep.setEnvironmentUrl(environmentUrl);
    }
}