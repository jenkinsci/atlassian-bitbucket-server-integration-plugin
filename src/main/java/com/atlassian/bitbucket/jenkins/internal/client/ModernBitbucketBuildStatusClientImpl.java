package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultInstanceIdentityProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceIdentityProvider;
import com.google.common.annotations.VisibleForTesting;
import okhttp3.HttpUrl;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ModernBitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BUILD_STATUS_SIGNATURE_ID = "BBS-Signature";
    private static final String BUILD_STATUS_SIGNATURE_ALGORITHM_ID = "BBS-Signature-Algorithm";
    private static final String BUILD_STATUS_VERSION = "1.0";
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final InstanceIdentityProvider instanceIdentityProvider;
    private final String projectKey;
    private final String repoSlug;
    private final String revisionSha;

    @VisibleForTesting
    ModernBitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey,
                                         String repoSlug, String revisionSha,
                                         InstanceIdentityProvider instanceIdentityProvider) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.instanceIdentityProvider = requireNonNull(instanceIdentityProvider, "instanceIdentityProvider");
        this.revisionSha = requireNonNull(stripToNull(revisionSha), "revisionSha");
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repoSlug = requireNonNull(stripToNull(repoSlug), "repoSlug");
    }

    ModernBitbucketBuildStatusClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectKey,
                                         String repoSlug, String revisionSha) {
        this(bitbucketRequestExecutor, projectKey, repoSlug, revisionSha, new DefaultInstanceIdentityProvider());
    }

    public Map<String, String> generateSignature(BitbucketBuildStatus buildStatus) {
        Map<String, String> headers = new HashMap<>();
        try {
            RSAPrivateKey key = instanceIdentityProvider.getInstanceIdentity().getPrivate();
            String algorithm = "SHA256 with" + key.getAlgorithm();
            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(key);

            sig.update(buildStatus.getKey().getBytes(UTF_8));
            sig.update(Objects.toString(buildStatus.getRef(), "").getBytes(UTF_8));
            sig.update(buildStatus.getState().getBytes(UTF_8));
            sig.update(buildStatus.getUrl().getBytes(UTF_8));

            headers.put(BUILD_STATUS_SIGNATURE_ID, Base64.getEncoder().encodeToString(sig.sign()));
            headers.put(BUILD_STATUS_SIGNATURE_ALGORITHM_ID, algorithm);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new BitbucketClientException("Exception signing build status", e);
        }
        return headers;
    }

    @Override
    public void post(BitbucketBuildStatus buildStatus) {
        HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment(BUILD_STATUS_VERSION)
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repoSlug)
                .addPathSegment("commits")
                .addPathSegment(revisionSha)
                .addPathSegment("builds")
                .addPathSegment(buildStatus.getKey())
                .build();
        bitbucketRequestExecutor.makePostRequest(url, buildStatus, generateSignature(buildStatus));
    }
}
