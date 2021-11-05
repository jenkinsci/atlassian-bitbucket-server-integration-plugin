package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeployment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironment;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import okhttp3.Request;
import okio.Buffer;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.apache.commons.lang3.StringUtils.normalizeSpace;
import static org.junit.Assert.assertEquals;

public class BitbucketDeploymentClientImplTest {

    private static final String DEPLOYMENTS_URL = "%s/rest/api/1.0/projects/%s/repos/%s/commits/%s/deployments";
    private static final String projectKey = "proj";
    private static final String repoSlug = "repo";
    private static final String revisionSha = "deadbeef";

    private final FakeRemoteHttpServer fakeRemoteHttpServer = new FakeRemoteHttpServer();
    private final HttpRequestExecutor requestExecutor = new HttpRequestExecutorImpl(fakeRemoteHttpServer);
    private final BitbucketRequestExecutor bitbucketRequestExecutor = new BitbucketRequestExecutor(BITBUCKET_BASE_URL,
            requestExecutor, OBJECT_MAPPER, ANONYMOUS_CREDENTIALS);
    private BitbucketDeploymentClient client = new BitbucketDeploymentClientImpl(bitbucketRequestExecutor, projectKey,
            repoSlug, revisionSha);

    @Test
    public void testSendDeployment() throws IOException {
        String response = readFileToString("/deployments/send_deployment_result.json");
        String requestBody = readFileToString("/deployments/send_deployment_request.json");
        String deploymentsUrl = format(DEPLOYMENTS_URL, BITBUCKET_BASE_URL, projectKey, repoSlug, revisionSha);
        fakeRemoteHttpServer.mapPostRequestToResult(deploymentsUrl, requestBody, response);

        BitbucketDeploymentEnvironment environment = new BitbucketDeploymentEnvironment("MY-ENV", "My-env",
                BitbucketDeploymentEnvironmentType.DEVELOPMENT, URI.create("http://url.to.env"));
        BitbucketDeployment deployment = new BitbucketDeployment(42, "my-description", "my-display-name", environment,
                "my-key", DeploymentState.FAILED, "http://url.to.job");
        client.post(deployment);

        Request recordedRequest = fakeRemoteHttpServer.getRequest(deploymentsUrl);
        Buffer b = new Buffer();
        recordedRequest.body().writeTo(b);
        assertEquals("Request body not same as expected.", deleteWhitespace(normalizeSpace(requestBody)), new String(b.readByteArray()));
    }
}