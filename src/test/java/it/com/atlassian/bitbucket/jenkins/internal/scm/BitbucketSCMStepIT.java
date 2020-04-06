package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.TestSCM;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.TestSCMStep;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class BitbucketSCMStepIT {

    private static final String CLONE_URL_HTTP = "localhost:7990/bitbucket/scm/project_1/rep_1.git";
    private static final String CLONE_URL_SSH = "localhost:7999/project_1/rep_1.git";
    private static final String BROWSE_URL = "http://localhost:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse";
    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String PROJECT_NAME = "Project 1";
    private static final String REPO_NAME = "rep_1";
    private static final String REPO_SLUG = "rep_1";
    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testCreateSCMHttp() {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = UUID.randomUUID().toString();
        String serverId = serverConf.getId();
        TestSCMStep scmStep = new TestSCMStep(id, singletonList(new BranchSpec("master")),
                credentialsId, "", PROJECT_NAME, REPO_NAME, serverId, "");
        assertThat(scmStep.getBranches(), hasSize(1));
        assertThat(scmStep.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scmStep.getCloneUrl(), containsStringIgnoringCase(CLONE_URL_HTTP));
        assertThat(scmStep.getCredentialsId(), equalTo(credentialsId));
        assertThat(scmStep.getId(), equalTo(id));
        assertThat(scmStep.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(scmStep.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(scmStep.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(scmStep.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(scmStep.getSelfLink(), equalToIgnoringCase(BROWSE_URL));
        assertThat(scmStep.getServerId(), equalTo(serverId));
        assertThat(scmStep.getSshCredentialsId(), equalTo(""));
        assertThat(scmStep.getMirrorName(), equalTo(""));

        TestSCM scm = scmStep.createSCM();
        assertThat(scm, instanceOf(BitbucketSCM.class));
        assertThat(scm.getBranches(), hasSize(1));
        assertThat(scm.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scm.getId(), equalTo(id));
        assertThat(scm.getRepositories(), hasSize(1));
        assertThat(scm.getRepositories().get(0), instanceOf(BitbucketSCMRepository.class));
        BitbucketSCMRepository bitbucketSCMRepository = scm.getRepositories().get(0);
        assertThat(bitbucketSCMRepository.getCredentialsId(), equalTo(credentialsId));
        assertThat(bitbucketSCMRepository.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(bitbucketSCMRepository.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(bitbucketSCMRepository.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(bitbucketSCMRepository.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(bitbucketSCMRepository.getServerId(), equalTo(serverId));
        assertThat(bitbucketSCMRepository.getSshCredentialsId(), equalTo(""));
        GitSCM gitSCM = scm.getGitSCM();
        assertThat(gitSCM.getRepositories(), hasSize(1));
        RemoteConfig remoteConfig = gitSCM.getRepositories().get(0);
        assertThat(remoteConfig.getURIs(), hasSize(1));
        URIish cloneUrl = remoteConfig.getURIs().get(0);
        assertThat(cloneUrl.toString(), containsStringIgnoringCase(CLONE_URL_HTTP));
    }

    @Test
    public void testCreateSCMSsh() {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String sshCredentialsId = bbJenkinsRule.getSshCredentialId();
        String id = UUID.randomUUID().toString();
        String serverId = serverConf.getId();
        TestSCMStep scmStep = new TestSCMStep(id, singletonList(new BranchSpec("master")),
                credentialsId, sshCredentialsId, PROJECT_NAME, REPO_NAME, serverId, "");
        assertThat(scmStep.getBranches(), hasSize(1));
        assertThat(scmStep.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scmStep.getCloneUrl(), containsStringIgnoringCase(CLONE_URL_SSH));
        assertThat(scmStep.getCredentialsId(), equalTo(credentialsId));
        assertThat(scmStep.getId(), equalTo(id));
        assertThat(scmStep.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(scmStep.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(scmStep.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(scmStep.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(scmStep.getSelfLink(), equalToIgnoringCase(BROWSE_URL));
        assertThat(scmStep.getServerId(), equalTo(serverId));
        assertThat(scmStep.getSshCredentialsId(), equalTo(sshCredentialsId));
        assertThat(scmStep.getMirrorName(), equalTo(""));

        TestSCM scm = scmStep.createSCM();
        assertThat(scm, instanceOf(BitbucketSCM.class));
        assertThat(scm.getBranches(), hasSize(1));
        assertThat(scm.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scm.getId(), equalTo(id));
        assertThat(scm.getRepositories(), hasSize(1));
        assertThat(scm.getRepositories().get(0), instanceOf(BitbucketSCMRepository.class));
        BitbucketSCMRepository bitbucketSCMRepository = scm.getRepositories().get(0);
        assertThat(bitbucketSCMRepository.getCredentialsId(), equalTo(credentialsId));
        assertThat(bitbucketSCMRepository.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(bitbucketSCMRepository.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(bitbucketSCMRepository.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(bitbucketSCMRepository.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(bitbucketSCMRepository.getServerId(), equalTo(serverId));
        assertThat(bitbucketSCMRepository.getSshCredentialsId(), equalTo(sshCredentialsId));
        GitSCM gitSCM = scm.getGitSCM();
        assertThat(gitSCM.getRepositories(), hasSize(1));
        RemoteConfig remoteConfig = gitSCM.getRepositories().get(0);
        assertThat(remoteConfig.getURIs(), hasSize(1));
        URIish cloneUrl = remoteConfig.getURIs().get(0);
        assertThat(cloneUrl.toString(), containsStringIgnoringCase(CLONE_URL_SSH));
    }
}