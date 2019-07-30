package com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BitbucketJenkinsRule extends JenkinsRule {

    private static final AtomicReference<PersonalToken> ADMIN_PERSONAL_TOKEN = new AtomicReference<>();
    private static final String BITBUCKET_ADMIN_PASSWORD =
            System.getProperty("bitbucket.admin.password", "admin");
    private static final String BITBUCKET_ADMIN_USERNAME =
            System.getProperty("bitbucket.admin.username", "admin");
    private static final String BITBUCKET_BASE_URL =
            System.getProperty("bitbucket.baseurl", "http://localhost:7990/bitbucket");
    private static final AtomicReference<PersonalToken> READ_PERSONAL_TOKEN = new AtomicReference<>();
    private BitbucketServerConfiguration bitbucketServer;

    @Override
    public void before() throws Throwable {
        super.before();

        if (ADMIN_PERSONAL_TOKEN.get() == null) {
            ADMIN_PERSONAL_TOKEN.set(createPersonalToken("REPO_ADMIN"));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(ADMIN_PERSONAL_TOKEN.get().getId()));
        }
        String adminCredentialsId = setupCredentials(ADMIN_PERSONAL_TOKEN.get().getSecret());

        if (READ_PERSONAL_TOKEN.get() == null) {
            READ_PERSONAL_TOKEN.set(createPersonalToken("PROJECT_READ"));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(ADMIN_PERSONAL_TOKEN.get().getId()));
        }
        String readCredentialsId = setupCredentials(ADMIN_PERSONAL_TOKEN.get().getSecret());

        bitbucketServer = new BitbucketServerConfiguration(adminCredentialsId, BITBUCKET_BASE_URL, readCredentialsId, null);
        addBitbucketServer(bitbucketServer);
    }

    public void addBitbucketServer(BitbucketServerConfiguration bitbucketServer) {
        ExtensionList<BitbucketPluginConfiguration> configExtensions = jenkins.getExtensionList(BitbucketPluginConfiguration.class);
        BitbucketPluginConfiguration configuration = configExtensions.get(0);
        configuration.getServerList().add(bitbucketServer);
        configuration.save();
    }

    public BitbucketServerConfiguration getBitbucketServer() {
        return bitbucketServer;
    }

    private PersonalToken createPersonalToken(String... permissions) {
        HashMap<String, Object> createTokenRequest = new HashMap<>();
        createTokenRequest.put("name", "BitbucketJenkinsRule-" + UUID.randomUUID());
        createTokenRequest.put("permissions", permissions);
        ResponseBody<Response> tokenResponse =
                RestAssured.given()
                        .log()
                        .ifValidationFails()
                        .auth()
                        .preemptive()
                        .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                        .contentType(ContentType.JSON)
                        .body(createTokenRequest)
                        .expect()
                        .statusCode(200)
                        .when()
                        .put(BITBUCKET_BASE_URL + "/rest/access-tokens/latest/users/admin")
                        .getBody();
        return new PersonalToken(tokenResponse.path("id"), tokenResponse.path("token"));
    }

    private String setupCredentials(String secret) throws Exception {
        String credentialId = UUID.randomUUID().toString();
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials =
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId, "", BITBUCKET_ADMIN_USERNAME, secret);
        store.addCredentials(domain, credentials);
        return credentialId;
    }

    private static final class BitbucketTokenCleanUpThread extends Thread {

        private final String tokenId;

        private BitbucketTokenCleanUpThread(String tokenId) {
            this.tokenId = tokenId;
        }

        @Override
        public void run() {
            RestAssured.given()
                    .log()
                    .all()
                    .auth()
                    .preemptive()
                    .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                    .contentType(ContentType.JSON)
                    .expect()
                    .statusCode(204)
                    .when()
                    .delete(
                            BITBUCKET_BASE_URL
                            + "/rest/access-tokens/latest/users/admin/"
                            + tokenId);
        }
    }

    private static final class PersonalToken {

        private final String id;
        private final String secret;

        private PersonalToken(String id, String secret) {
            this.id = id;
            this.secret = secret;
        }

        public String getId() {
            return id;
        }

        public String getSecret() {
            return secret;
        }
    }
}
