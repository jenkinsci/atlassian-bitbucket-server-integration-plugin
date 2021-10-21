package it.com.atlassian.bitbucket.jenkins.internal.deployments;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Test;
import wiremock.org.apache.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState.SUCCESSFUL;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler.MASTER_BRANCH_PATTERN;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.PROJECT_KEY;
import static java.lang.String.format;

public class PipelineDeploymentStatusPosterIT extends AbstractDeploymentStatusPosterIT {

    @Test
    public void testPipelineWithBitbucketSCM() throws Exception {
        WorkflowJob job = jenkinsProjectHandler.createPipelineJobWithBitbucketScm("wfj", PROJECT_KEY, repoSlug, MASTER_BRANCH_PATTERN);
        String environmentName = "Prod";
        String latestCommit = checkInJenkinsFile("deployments/DeploymentJenkinsfile", "bbs_deploy(environmentName: '" + environmentName + "')");

        String url = getDeploymentUrl(latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        String environment = format("{" +
                "   \"displayName\":\"%s\"" +
                "}", environmentName);
        jenkinsProjectHandler.runPipelineJob(job, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, SUCCESSFUL, environmentName, environment));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testPipelineWithCheckoutInJenkinsfile() throws Exception {
        String environmentName = "prod";
        String jenkinsfile = format(IOUtils.toString(getClass().getClassLoader().getResourceAsStream("deployments/DeploymentJenkinsfileWithCheckout"), StandardCharsets.UTF_8),
                bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId(),
                PROJECT_KEY,
                repoSlug,
                bbJenkinsRule.getBitbucketServerConfiguration().getId(),
                environmentName);
        WorkflowJob job = jenkinsProjectHandler.createPipelineJob("workflow", jenkinsfile);

        String url = getDeploymentUrl(gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        String environment = format("{" +
                "   \"displayName\":\"%s\"" +
                "}", environmentName);
        jenkinsProjectHandler.runPipelineJob(job, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, SUCCESSFUL, environmentName, environment));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testMultibranchWithBitbucketSCM() throws Exception {
        WorkflowMultiBranchProject mbp = jenkinsProjectHandler.createMultibranchJob("mbp", PROJECT_KEY, repoSlug);

        jenkinsProjectHandler.performBranchScanning(mbp);

        String environmentName = "Prod";
        String latestCommit = checkInJenkinsFile("deployments/DeploymentJenkinsfile", "bbs_deploy(environmentName: '" + environmentName + "')");
        String environment = format("{" +
                "   \"displayName\":\"%s\"" +
                "}", environmentName);

        String url = getDeploymentUrl(latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.performBranchScanningAndWaitForBuild(mbp, "master");
        jenkinsProjectHandler.runWorkflowJobForBranch(mbp, "master", build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, SUCCESSFUL, environmentName, environment));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testMultibranchWithCheckoutInJenkinsfile() throws Exception {
        WorkflowMultiBranchProject mbp = jenkinsProjectHandler.createMultibranchJob("mbp", PROJECT_KEY, repoSlug);

        jenkinsProjectHandler.performBranchScanning(mbp);

        String environmentName = "Prod";
        String environment = format("{" +
                "   \"displayName\":\"%s\"" +
                "}", environmentName);
        String latestCommit = checkInJenkinsFile("deployments/DeploymentJenkinsfileWithCheckout",
                bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId(),
                PROJECT_KEY,
                repoSlug,
                bbJenkinsRule.getBitbucketServerConfiguration().getId(),
                environmentName);

        String url = getDeploymentUrl(latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.performBranchScanningAndWaitForBuild(mbp, "master");
        jenkinsProjectHandler.runWorkflowJobForBranch(mbp, "master", build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, SUCCESSFUL, environmentName, environment));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String checkInJenkinsFile(String jenkinsfilePath, String... args) throws Exception {
        String jenkinsfile = IOUtils.toString(getClass().getClassLoader().getResourceAsStream(jenkinsfilePath), StandardCharsets.UTF_8);
        return gitHelper.addFileToRepo("master", "Jenkinsfile", format(jenkinsfile, args));
    }
}
