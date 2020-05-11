package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.provider.DefaultInstanceIdentityProvider;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceIdentityProvider;
import com.google.common.annotations.VisibleForTesting;
import okhttp3.HttpUrl;

import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class ModernBitbucketBuildStatusClientImpl implements BitbucketBuildStatusClient {

    private static final String BBS_BUILD_STATUS_SIGNATURE_ID = "BBS-Signature";
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
            Signature sig = Signature.getInstance("SHA256with" + key.getAlgorithm());
            sig.initSign(key);
            sig.update(buildStatus.getSignature().getBytes("UTF-8"));
            headers.put(BBS_BUILD_STATUS_SIGNATURE_ID, Base64.getEncoder().encodeToString(sig.sign()));
        } catch (Exception e) {
            throw new BitbucketClientException("Exception signing build status", e);
        }
        return headers;
    }

    @Override
    public void post(BitbucketBuildStatus buildStatus) {
        //projects/PROJECT_1/repos/rep_1/commits/abc1234avcacac/builds/buildkey
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
