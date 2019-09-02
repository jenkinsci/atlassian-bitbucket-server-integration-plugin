package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor.BearerCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketWebhook;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegisterResult;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.hamcrest.core.StringContains;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.*;

/**
 * Integration tests for testing integration of Webhook handler code with actual bitbucket server. Unfortunately,
 * test against mirror can't be performed as the bitbucket version we run the test against does not support mirrors.
 */
public class RetryingWebhookHandlerIT {

    private static final String WEBHOOK_NAME = RetryingWebhookHandlerIT.class.getSimpleName();
    private HttpRequestExecutor httpRequestExecutor = new HttpRequestExecutorImpl();
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider =
            new BitbucketClientFactoryProvider(httpRequestExecutor);
    private PersonalToken adminToken;
    private PersonalToken nonAdminToken;
    private BitbucketCredentials adminCredentials;
    private BitbucketCredentials nonAdminCredentials;

    @Before
    public void setup() {
        adminToken = createPersonalToken(BitbucketUtils.REPO_ADMIN_PERMISSION);
        nonAdminToken = createPersonalToken(BitbucketUtils.PROJECT_READ_PERMISSION);
        adminCredentials = new BearerCredentials(adminToken.getSecret());
        nonAdminCredentials = new BearerCredentials(nonAdminToken.getSecret());
        cleanWebhooks();
    }

    @After
    public void teardown() {
        cleanWebhooks();
        deletePersonalToken(adminToken.getId());
        deletePersonalToken(nonAdminToken.getId());
    }

    @Test
    public void testWebhookRegister() {
        String callbackUrl = "http://localhost:8080/jenkins";
        RetryingWebhookHandler webhookHandler = getInstance(adminCredentials, adminCredentials);

        WebhookRegisterResult result = webhookHandler.register(WebhookRegisterRequest.Builder
                .aRequest(PROJECT_KEY, REPO_SLUG)
                .withJenkinsBaseUrl(callbackUrl)
                .withName(WEBHOOK_NAME)
                .isMirror(false)
                .build());

        assertTrue(result.isSuccess());
        assertThat(result.getWebhook().getUrl(), StringContains.containsString(callbackUrl));
        assertThat(result.getWebhook().getEvents(), iterableWithSize(1));
        assertThat(result.getWebhook().getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testRegisterUsingFallbackCredentials() {
        String callbackUrl = "http://localhost:8080/jenkins";
        RetryingWebhookHandler webhookHandler = getInstance(nonAdminCredentials, adminCredentials);

        WebhookRegisterResult result = webhookHandler.register(WebhookRegisterRequest.Builder
                .aRequest(PROJECT_KEY, REPO_SLUG)
                .withJenkinsBaseUrl(callbackUrl)
                .withName(WEBHOOK_NAME)
                .isMirror(false)
                .build());

        assertTrue(result.isSuccess());
        assertThat(result.getWebhook().getUrl(), StringContains.containsString(callbackUrl));
        assertThat(result.getWebhook().getEvents(), iterableWithSize(1));
        assertThat(result.getWebhook().getEvents(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    @Test
    public void testOneWebhookPerRepository() {
        String callbackUrl = "http://localhost:8080/jenkins";
        RetryingWebhookHandler webhookHandler = getInstance(nonAdminCredentials, adminCredentials);

        WebhookRegisterResult result1 = webhookHandler.register(WebhookRegisterRequest.Builder
                .aRequest(PROJECT_KEY, REPO_SLUG)
                .withJenkinsBaseUrl(callbackUrl)
                .withName(WEBHOOK_NAME)
                .isMirror(false)
                .build());
        assertTrue(result1.isSuccess());

        WebhookRegisterResult result2 = webhookHandler.register(WebhookRegisterRequest.Builder
                .aRequest(PROJECT_KEY, REPO_SLUG)
                .withJenkinsBaseUrl(callbackUrl)
                .withName(WEBHOOK_NAME + "_123")
                .isMirror(false)
                .build());
        assertFalse(result2.isSuccess());
        assertTrue(result2.isAlreadyRegistered());
        assertThat(result1.getWebhook().getId(), is(equalTo(result2.getWebhook().getId())));
    }

    private void cleanWebhooks() {
        bitbucketClientFactoryProvider.getClient(BitbucketJenkinsRule.BITBUCKET_BASE_URL, adminCredentials)
                .getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getWebhookClient()
                .getWebhooks()
                .filter(bitbucketWebhook -> bitbucketWebhook.getName().startsWith(WEBHOOK_NAME))
                .map(BitbucketWebhook::getId)
                .forEach(id -> deleteWebhook(PROJECT_KEY, REPO_SLUG, id));
    }

    private RetryingWebhookHandler getInstance(BitbucketCredentials primary,
                                               BitbucketCredentials secondary) {
        return new RetryingWebhookHandler(bitbucketClientFactoryProvider,
                BitbucketJenkinsRule.BITBUCKET_BASE_URL,
                primary,
                secondary);
    }
}
