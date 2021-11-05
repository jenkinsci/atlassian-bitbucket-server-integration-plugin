package it.com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.deployments.DeploymentNotifier;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Test;
import wiremock.org.apache.http.HttpStatus;

import static com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState.IN_PROGRESS;
import static com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState.SUCCESSFUL;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler.MASTER_BRANCH_PATTERN;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.PROJECT_KEY;
import static java.lang.String.format;

public class FreestyleDeploymentStatusPosterIT extends AbstractDeploymentStatusPosterIT {

    @Test
    public void testWithEnvironmentName() throws Exception {
        String environmentName = "prod";
        DeploymentNotifier notifier = new DeploymentNotifier(environmentName);
        FreeStyleProject project =
                jenkinsProjectHandler.createFreeStyleProject(PROJECT_KEY, repoSlug, MASTER_BRANCH_PATTERN,
                        unsavedProject -> unsavedProject.getPublishersList().add(notifier));

        String url = getDeploymentUrl(gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        String environment = format("{" +
                "   \"displayName\":\"%s\"," +
                "   \"key\":\"%s\"" +
                "}", environmentName, notifier.getEnvironmentKey());
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, IN_PROGRESS, environmentName, environment));
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, SUCCESSFUL, environmentName, environment));
    }

    @Test
    public void testWithEnvironmentType() throws Exception {
        String environmentName = "prod";
        DeploymentNotifier notifier = new DeploymentNotifier(environmentName);
        BitbucketDeploymentEnvironmentType environmentType = BitbucketDeploymentEnvironmentType.PRODUCTION;
        notifier.setEnvironmentType(environmentType.getDisplayName());
        FreeStyleProject project =
                jenkinsProjectHandler.createFreeStyleProject(PROJECT_KEY, repoSlug, MASTER_BRANCH_PATTERN,
                        unsavedProject -> unsavedProject.getPublishersList().add(notifier));

        String url = getDeploymentUrl(gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        String environment = format("{" +
                "   \"displayName\":\"%s\"," +
                "   \"type\":\"%s\"," +
                "   \"key\":\"%s\"" +
                "}", environmentName, environmentType.name(), notifier.getEnvironmentKey());
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, IN_PROGRESS, environmentName, environment));
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, SUCCESSFUL, environmentName, environment));
    }

    @Test
    public void testWithEnvironmentUrl() throws Exception {
        String environmentName = "prod";
        DeploymentNotifier notifier = new DeploymentNotifier(environmentName);
        String environmentUrl = "http://localhost:8080/prod";
        notifier.setEnvironmentUrl(environmentUrl);
        FreeStyleProject project =
                jenkinsProjectHandler.createFreeStyleProject(PROJECT_KEY, repoSlug, MASTER_BRANCH_PATTERN,
                        unsavedProject -> unsavedProject.getPublishersList().add(notifier));

        String url = getDeploymentUrl(gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        String environment = format("{" +
                "   \"displayName\":\"%s\"," +
                "   \"url\":\"%s\"," +
                "   \"key\":\"%s\"" +
                "}", environmentName, environmentUrl, notifier.getEnvironmentKey());
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, IN_PROGRESS, environmentName, environment));
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, SUCCESSFUL, environmentName, environment));
    }

    @Test
    public void testWithEnvironmentKey() throws Exception {
        String environmentName = "prod";
        DeploymentNotifier notifier = new DeploymentNotifier(environmentName);
        String environmentKey = "my-key";
        notifier.setEnvironmentKey(environmentKey);
        FreeStyleProject project =
                jenkinsProjectHandler.createFreeStyleProject(PROJECT_KEY, repoSlug, MASTER_BRANCH_PATTERN,
                        unsavedProject -> unsavedProject.getPublishersList().add(notifier));

        String url = getDeploymentUrl(gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        String environment = format("{" +
                "   \"displayName\":\"%s\"," +
                "   \"key\" : \"%s\"" +
                "}", environmentName, environmentKey);
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, IN_PROGRESS, environmentName, environment));
        verify(requestBody(postRequestedFor(urlPathMatching(url)), build, SUCCESSFUL, environmentName, environment));
    }
}
