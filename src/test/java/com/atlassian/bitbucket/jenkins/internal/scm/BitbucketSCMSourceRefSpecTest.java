package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestRef;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.trait.BitbucketPullRequestDiscoveryTrait;
import com.atlassian.bitbucket.jenkins.internal.scm.trait.BitbucketRefSpecsSCMSourceTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import java.util.Arrays;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class BitbucketSCMSourceRefSpecTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testRefSpecTraitOfferedInConfigurationUi() {
        BitbucketSCMSource.DescriptorImpl descriptor = jenkins.get(BitbucketSCMSource.DescriptorImpl.class);

        boolean offered = descriptor.getTraitsDescriptorLists().stream()
                .flatMap(list -> list.stream())
                .anyMatch(d -> d instanceof BitbucketRefSpecsSCMSourceTrait.DescriptorImpl);

        org.junit.Assert.assertTrue("Refspecs trait should be selectable", offered);
    }

    @Test
    public void testDefaultRefSpecUsedWithoutRefSpecTrait() throws Exception {
        assertThat(refSpecsFor(emptyList()), contains("+refs/heads/*:refs/remotes/origin/*"));
    }

    @Test
    public void testRefSpecTraitReplacesDefault() throws Exception {
        List<SCMSourceTrait> traits = singletonList(new BitbucketRefSpecsSCMSourceTrait(singletonList(
                new RefSpecsSCMSourceTrait.RefSpecTemplate("+refs/heads/develop:refs/remotes/@{remote}/develop"))));

        assertThat(refSpecsFor(traits), contains("+refs/heads/develop:refs/remotes/origin/develop"));
    }

    @Test
    public void testPullRequestBuildFetchesOnlyConfiguredRefSpecAndPullRequestRef() throws Exception {
        BitbucketPullRequestSCMHead head = pullRequestHead();
        List<SCMSourceTrait> traits = Arrays.asList(
                new BitbucketPullRequestDiscoveryTrait(),
                new BitbucketRefSpecsSCMSourceTrait(singletonList(
                        new RefSpecsSCMSourceTrait.RefSpecTemplate("+refs/heads/develop:refs/remotes/@{remote}/develop"))));

        assertThat(refSpecsFor(traits, head, new BitbucketPullRequestSCMRevision(head)),
                containsInAnyOrder("+refs/pull-requests/7/from:refs/remotes/origin/PR-7",
                        "+refs/heads/develop:refs/remotes/origin/develop"));
    }

    @Test
    public void testPullRequestBuildWithoutRefSpecTraitStillFetchesAllBranches() throws Exception {
        BitbucketPullRequestSCMHead head = pullRequestHead();

        assertThat(refSpecsFor(singletonList(new BitbucketPullRequestDiscoveryTrait()), head,
                new BitbucketPullRequestSCMRevision(head)),
                containsInAnyOrder("+refs/heads/*:refs/remotes/origin/*",
                        "+refs/pull-requests/7/from:refs/remotes/origin/PR-7"));
    }

    private static BitbucketPullRequestSCMHead pullRequestHead() {
        BitbucketRepository repo = mock(BitbucketRepository.class);
        return new BitbucketPullRequestSCMHead(new BitbucketPullRequest(7, BitbucketPullRequestState.OPEN,
                new BitbucketPullRequestRef("refs/heads/feature", "feature", repo, "fromCommit"),
                new BitbucketPullRequestRef("refs/heads/develop", "develop", repo, "toCommit"),
                -1, "title", "description"));
    }

    private List<String> refSpecsFor(List<SCMSourceTrait> traits) throws Exception {
        return refSpecsFor(traits, new BitbucketBranchSCMHead("develop"), null);
    }

    private List<String> refSpecsFor(List<SCMSourceTrait> traits, SCMHead head, SCMRevision revision)
            throws Exception {
        BitbucketSCMSource source =
                new BitbucketSCMSource("1", "cred", null, traits, "PROJ", "repo", "server", null);
        set(source, "cloneUrl", "http://localhost:7990/scm/proj/repo.git");
        set(source, "selfLink", "http://localhost:7990/projects/PROJ/repos/repo");
        set(source, "initialized", new AtomicBoolean(true));

        GitSCM scm = (GitSCM) source.build(head, revision);
        UserRemoteConfig config = scm.getUserRemoteConfigs().get(0);
        return java.util.Arrays.asList(config.getRefspec().split(" "));
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = BitbucketSCMSource.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
