package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.TestSCMStep;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class BitbucketSCMStepTest {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String PROJECT_NAME = "Project 1";
    private static final String REPO_NAME = "rep_1";
    private static final String REPO_SLUG = "rep_1";
    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testCreateSCM() {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = UUID.randomUUID().toString();
        String serverId = serverConf.getId();
        TestSCMStep scmStep = new TestSCMStep(id, singletonList(new BranchSpec("master")),
                credentialsId, PROJECT_NAME, REPO_NAME, serverId, "");
        assertThat(scmStep.getBranches(), hasSize(1));
        assertThat(scmStep.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scmStep.getCloneUrl(), equalTo("http://localhost:7990/bitbucket/scm/project_1/rep_1.git"));
        assertThat(scmStep.getCredentialsId(), equalTo(credentialsId));
        assertThat(scmStep.getId(), equalTo(id));
        assertThat(scmStep.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(scmStep.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(scmStep.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(scmStep.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(scmStep.getRepositoryId(), equalTo(1));
        assertThat(scmStep.getSelfLink(), equalTo("http://localhost:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse"));
        assertThat(scmStep.getServerId(), equalTo(serverId));
        assertThat(scmStep.getMirrorName(), equalTo(""));

        SCM scm = scmStep.createSCM();
        assertThat(scm, instanceOf(BitbucketSCM.class));
        BitbucketSCM bitbucketSCM = (BitbucketSCM) scm;
        assertThat(bitbucketSCM.getBranches(), hasSize(1));
        assertThat(bitbucketSCM.getBranches().get(0).getName(), equalTo("master"));
        assertThat(bitbucketSCM.getId(), equalTo(id));
        assertThat(bitbucketSCM.getRepositories(), hasSize(1));
        assertThat(bitbucketSCM.getRepositories().get(0), instanceOf(BitbucketSCMRepository.class));
        BitbucketSCMRepository bitbucketSCMRepository = bitbucketSCM.getRepositories().get(0);
        assertThat(bitbucketSCMRepository.getCredentialsId(), equalTo(credentialsId));
        assertThat(bitbucketSCMRepository.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(bitbucketSCMRepository.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(bitbucketSCMRepository.getRepositoryName(), equalTo(REPO_NAME));
        assertThat(bitbucketSCMRepository.getRepositorySlug(), equalTo(REPO_SLUG));
        assertThat(bitbucketSCMRepository.getServerId(), equalTo(serverId));
        GitSCM gitSCM = bitbucketSCM.getGitSCM();
        assertThat(gitSCM.getRepositories(), hasSize(1));
        RemoteConfig remoteConfig = gitSCM.getRepositories().get(0);
        assertThat(remoteConfig.getURIs(), hasSize(1));
        URIish cloneUrl = remoteConfig.getURIs().get(0);
        assertThat(cloneUrl, equalTo("http://localhost:7990/bitbucket/scm/project_1/rep_1.git"));
    }
}