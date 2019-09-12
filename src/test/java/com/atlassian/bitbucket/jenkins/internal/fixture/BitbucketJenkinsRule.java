package com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.util.SecretFactory;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;

public class BitbucketJenkinsRule extends JenkinsRule {

    public static final String SERVER_NAME = "Bitbucket server";

    private static final AtomicReference<PersonalToken> ADMIN_PERSONAL_TOKEN = new AtomicReference<>();
    private static final AtomicReference<PersonalToken> READ_PERSONAL_TOKEN = new AtomicReference<>();
    private BitbucketServerConfiguration bitbucketServerConfiguration;
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;

    public void addBitbucketServer(BitbucketServerConfiguration bitbucketServer) {
        ExtensionList<BitbucketPluginConfiguration> configExtensions =
                jenkins.getExtensionList(BitbucketPluginConfiguration.class);
        bitbucketPluginConfiguration = configExtensions.get(0);
        bitbucketPluginConfiguration.getServerList().add(bitbucketServer);
        bitbucketPluginConfiguration.save();
    }

    @Override
    public void before() throws Throwable {
        super.before();

        if (ADMIN_PERSONAL_TOKEN.get() == null) {
            ADMIN_PERSONAL_TOKEN.set(createPersonalToken(REPO_ADMIN_PERMISSION));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(ADMIN_PERSONAL_TOKEN.get().getId()));
        }
        String adminCredentialsId = UUID.randomUUID().toString();
        Credentials adminCredentials = new BitbucketTokenCredentialsImpl(CredentialsScope.GLOBAL, adminCredentialsId,
                "", SecretFactory.getSecret(ADMIN_PERSONAL_TOKEN.get().getSecret()));
        addCredentials(adminCredentials);

        if (READ_PERSONAL_TOKEN.get() == null) {
            READ_PERSONAL_TOKEN.set(createPersonalToken(PROJECT_READ_PERMISSION));
            Runtime.getRuntime().addShutdownHook(new BitbucketTokenCleanUpThread(ADMIN_PERSONAL_TOKEN.get().getId()));
        }
        String readCredentialsId = UUID.randomUUID().toString();
        Credentials readCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, readCredentialsId,
                "", BITBUCKET_ADMIN_USERNAME, ADMIN_PERSONAL_TOKEN.get().getSecret());
        addCredentials(readCredentials);

        bitbucketServerConfiguration =
                new BitbucketServerConfiguration(adminCredentialsId, BITBUCKET_BASE_URL, readCredentialsId, null);
        bitbucketServerConfiguration.setServerName(SERVER_NAME);
        addBitbucketServer(bitbucketServerConfiguration);
    }

    public BitbucketServerConfiguration getBitbucketServerConfiguration() {
        return bitbucketServerConfiguration;
    }

    public BitbucketPluginConfiguration getBitbucketPluginConfiguration() {
        return bitbucketPluginConfiguration;
    }

    private void addCredentials(Credentials credentials) throws IOException {
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        store.addCredentials(domain, credentials);
    }

    private static final class BitbucketTokenCleanUpThread extends Thread {

        private final String tokenId;

        private BitbucketTokenCleanUpThread(String tokenId) {
            this.tokenId = tokenId;
        }

        @Override
        public void run() {
            BitbucketUtils.deletePersonalToken(tokenId);
        }
    }
}
