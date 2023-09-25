package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.model.TestResults;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.Nullable;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class ModernBitbucketBuildStatusClientImplTest {

    private static final String BBS_BASE_URL = "http://localhost:7990/bitbucket";
    private static final String JENKINS_BASE_URL = "http://localhost:8080/jenkins";
    private static final String SHA1 = "5ab78046a50050b8aa3e4accf80950a60f716391";
    private static final HttpUrl
            EXPECTED_URL = HttpUrl.parse(BBS_BASE_URL + "/rest/api/1.0/projects/PROJ/repos/repo/commits/" + SHA1 + "/builds");
    static KeyPair keyPair;
    @Captor
    ArgumentCaptor<RequestConfiguration> captor;
    ModernBitbucketBuildStatusClientImpl client;
    @Mock
    DisplayURLProvider displayURLProvider;
    @Mock
    BitbucketRequestExecutor executor;
    @Mock
    InstanceKeyPairProvider keyPairProvider;

    @BeforeClass
    public static void init() {
        keyPair = TestUtils.createTestKeyPair();
    }

    @Before
    public void setup() {
        when(displayURLProvider.getRoot()).thenReturn(JENKINS_BASE_URL);
        when(keyPairProvider.getPrivate()).thenReturn((RSAPrivateKey) keyPair.getPrivate());
        when(executor.getBaseUrl()).thenReturn(HttpUrl.parse(BBS_BASE_URL));
        
        client = new ModernBitbucketBuildStatusClientImpl(executor, "PROJ", "repo", SHA1, keyPairProvider, displayURLProvider, true);
    }

    @Test
    public void testPost() {
        BitbucketBuildStatus.Builder buildStatusBuilder = createTestBuildStatus("refs/testref");
        BitbucketBuildStatus buildStatus = buildStatusBuilder.build();
        Consumer<BitbucketBuildStatus> consumer = (Consumer<BitbucketBuildStatus>) mock(Consumer.class);
        
        client.post(buildStatusBuilder, consumer);

        verify(executor).makePostRequest(eq(EXPECTED_URL), eq(buildStatus), captor.capture());
        verify(consumer).accept(buildStatus);

        //this shortcuts the testing route. We capture the RequestConfiguration applied, and just give it a fake
        // Request.Builder and we can then assert it did the right thing from that.
        List<RequestConfiguration> configs = captor.getAllValues();
        Request.Builder builder = new Request.Builder().url("http://notUsed");
        configs.forEach(requestConfiguration -> requestConfiguration.apply(builder)
        );

        Headers headers = builder.build().headers();
        assertThat(headers.get("BBS-Signature-Algorithm"), equalTo("SHA256withRSA"));
        assertThat(headers.get("base-url"), equalTo(JENKINS_BASE_URL));
        assertTrue(matchSignature(buildStatusBuilder.build(), headers.get("BBS-Signature")));
    }

    @Test
    public void testPostNoCancelledState() {
        client = new ModernBitbucketBuildStatusClientImpl(executor, "PROJ", "repo", SHA1, keyPairProvider, displayURLProvider, false);

        BitbucketBuildStatus.Builder buildStatusBuilder = createTestBuildStatus("refs/testref");
        BitbucketBuildStatus buildStatus = buildStatusBuilder.noCancelledState().build();
        Consumer<BitbucketBuildStatus> consumer = (Consumer<BitbucketBuildStatus>) mock(Consumer.class);
        
        client.post(buildStatusBuilder, consumer);

        verify(executor).makePostRequest(eq(EXPECTED_URL), eq(buildStatus), captor.capture());
        verify(consumer).accept(buildStatus);
    }

    @Test
    public void testPostNoRef() {
        BitbucketBuildStatus.Builder buildStatusBuilder = createTestBuildStatus(null);
        BitbucketBuildStatus buildStatus = buildStatusBuilder.build();
        Consumer<BitbucketBuildStatus> consumer = (Consumer<BitbucketBuildStatus>) mock(Consumer.class);

        client.post(buildStatusBuilder, consumer);

        verify(executor).makePostRequest(eq(EXPECTED_URL), eq(buildStatus), captor.capture());
        verify(consumer).accept(buildStatus);

        //this shortcuts the testing route. We capture the RequestConfiguration applied, and just give it a fake
        // Request.Builder and we can then assert it did the right thing from that.
        List<RequestConfiguration> configs = captor.getAllValues();
        Request.Builder builder = new Request.Builder().url("http://notUsed");
        configs.forEach(requestConfiguration -> requestConfiguration.apply(builder)
        );

        Headers headers = builder.build().headers();
        assertThat(headers.get("BBS-Signature-Algorithm"), equalTo("SHA256withRSA"));
        assertThat(headers.get("base-url"), equalTo(JENKINS_BASE_URL));
        assertTrue(matchSignature(buildStatusBuilder.build(), headers.get("BBS-Signature")));
    }

    private BitbucketBuildStatus.Builder createTestBuildStatus(@Nullable String ref) {
        return new BitbucketBuildStatus.Builder("REPO-42", BuildState.FAILED, "http://example.com/builds/repo-42")
                .setDescription("Test description")
                .setTestResults(new TestResults(42, 21, 12))
                .setName("Test-Name")
                .setDuration(21L)
                .setRef(StringUtils.stripToNull(ref));
    }

    private boolean matchSignature(BitbucketBuildStatus buildStatus, @Nullable String signature) {
        if (signature == null) {
            return false;
        }

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        try {
            Signature verifySignature = Signature.getInstance("SHA256withRSA");
            verifySignature.initVerify(publicKey);
            verifySignature.update(buildStatus.getKey().getBytes(UTF_8));
            if (buildStatus.getRef() != null) {
                verifySignature.update(buildStatus.getRef().getBytes(UTF_8));
            }
            verifySignature.update(buildStatus.getState().getBytes(UTF_8));
            verifySignature.update(buildStatus.getUrl().getBytes(UTF_8));
            return verifySignature.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}