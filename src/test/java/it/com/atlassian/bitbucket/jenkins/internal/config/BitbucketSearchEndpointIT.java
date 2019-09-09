package it.com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.HudsonResponse;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class BitbucketSearchEndpointIT {

    private static final String FIND_MIRRORED_REPOS_URL = "bitbucket-server-search/findMirroredRepositories";
    @ClassRule
    public static BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testFindMirroredRepositories() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketMirroredRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                        .queryParam("repositoryId", 1)
                        .get(bitbucketJenkinsRule.getURL() + FIND_MIRRORED_REPOS_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketMirroredRepository>>>() {
                        });

        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<BitbucketMirroredRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<BitbucketMirroredRepository> values = results.getValues();
        assertThat(values.size(), equalTo(0));
    }
}
