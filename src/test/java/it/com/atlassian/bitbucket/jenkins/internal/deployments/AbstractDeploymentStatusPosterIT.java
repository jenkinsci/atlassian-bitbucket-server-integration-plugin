package it.com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.deployment.DeploymentState;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketProxyRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.GitHelper;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import jenkins.branch.MultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.lang.String.format;
import static org.junit.Assert.assertNotNull;

/**
 * Following tests use a real Bitbucket Server and Jenkins instance for integration testing, however, the
 * deployment status is posted against a stub.
 */
public abstract class AbstractDeploymentStatusPosterIT {

    protected final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    protected final BitbucketProxyRule bitbucketProxyRule = new BitbucketProxyRule(bbJenkinsRule);
    protected final GitHelper gitHelper = new GitHelper(bbJenkinsRule);

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Timeout testTimeout = new Timeout(0, TimeUnit.MINUTES);
    @Rule
    public final TestRule chain = RuleChain.outerRule(temporaryFolder)
            .around(testTimeout)
            .around(bitbucketProxyRule.getRule());

    protected JenkinsProjectHandler jenkinsProjectHandler;
    protected String repoSlug;

    @Before
    public void setUp() throws Exception {
        BitbucketRepository repository = forkRepository(PROJECT_KEY, REPO_SLUG);
        repoSlug = repository.getSlug();
        String cloneUrl =
                repository.getCloneUrls()
                        .stream()
                        .filter(repo ->
                                "http".equals(repo.getName()))
                        .findFirst()
                        .map(BitbucketNamedLink::getHref)
                        .orElseThrow(() -> new RuntimeException("Could not get the clone URL of the repo"));
        gitHelper.initialize(temporaryFolder.newFolder("repositoryCheckout"), cloneUrl);
        jenkinsProjectHandler = new JenkinsProjectHandler(bbJenkinsRule);
    }

    @After
    public void teardown() {
        jenkinsProjectHandler.cleanup();
        deleteRepository(PROJECT_KEY, repoSlug);
        gitHelper.cleanup();
    }

    protected String getDeploymentUrl(String commitId) {
        return format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/deployments", PROJECT_KEY, repoSlug, commitId);
    }

    protected RequestPatternBuilder requestBody(RequestPatternBuilder requestPatternBuilder, Run<?, ?> build,
                                                DeploymentState deploymentState, String environmentName, String environment) throws IOException {
        Job<?, ?> job = build.getParent();
        BitbucketRevisionAction bitbucketRevisionAction = build.getAction(BitbucketRevisionAction.class);
        assertNotNull(bitbucketRevisionAction);
        String jenkinsUrlAsString = bbJenkinsRule.getURL().toExternalForm();
        ItemGroup<?> parentProject = job.getParent();
        boolean isMultibranch = parentProject instanceof MultiBranchProject;
        String name = isMultibranch ? parentProject.getDisplayName() + " » " + job.getDisplayName() : job.getDisplayName();
        String requestPattern = "{" +
                "\"deploymentSequenceNumber\":%d," +
                "\"description\":\"%s\"," +
                "\"displayName\":\"%s\"," +
                "\"environment\":%s," +
                "\"key\":\"%s\"," +
                "\"state\":\"%s\"," +
                "\"url\":\"%s%sdisplay/redirect\"" +
                "}";
        String jsonString = format(requestPattern, build.getNumber(), deploymentState.getDescriptiveText(name,
                environmentName), name, environment, job.getFullName(), deploymentState, jenkinsUrlAsString,
                build.getUrl());
        return requestPatternBuilder.withRequestBody(equalToJson(jsonString));
    }
}
