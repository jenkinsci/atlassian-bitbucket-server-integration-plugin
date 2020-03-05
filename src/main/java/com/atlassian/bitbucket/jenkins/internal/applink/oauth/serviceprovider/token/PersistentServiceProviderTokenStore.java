package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Authorization;
import com.google.common.annotations.VisibleForTesting;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.NamedMapConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.BulkChange;
import hudson.CopyOnWrite;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.util.CopyOnWriteMap;
import hudson.util.Secret;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.util.io.OnMaster;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Authorization.AUTHORIZED;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newAccessToken;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newRequestToken;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenUtils.isTokenExpired;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenUtils.isTokenSessionExpired;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A {@link ServiceProviderTokenStore} implementation that persists the {@link ServiceProviderToken tokens} to an XML
 * file
 * <p>
 * Only one instance of this class must be created per Jenkins instance.
 *
 * @see Saveable
 * @see XStream2
 */
@Singleton
public class PersistentServiceProviderTokenStore implements ServiceProviderTokenStore, Saveable, OnMaster {

    private static final Logger log = Logger.getLogger(PersistentServiceProviderTokenStore.class.getName());

    private static final String TOKEN_STORE_ENTRY_NAME = "oauth-token";
    private static final String TOKEN_STORE_KEY_NAME = "token-value";
    private static final String TOKEN_STORE_VALUE_NAME = "token-details";

    @VisibleForTesting
    final transient XStream2 tokenStream;

    @CopyOnWrite
    @VisibleForTesting
    volatile Map<String, ServiceProviderToken> tokenMap;

    @Inject
    public PersistentServiceProviderTokenStore(ConsumerStore consumerStore) {
        tokenStream = new XStream2();
        tokenStream.registerConverter(new NamedMapConverter(tokenStream.getMapper(), TOKEN_STORE_ENTRY_NAME,
                TOKEN_STORE_KEY_NAME, String.class, TOKEN_STORE_VALUE_NAME, ServiceProviderToken.class), 100);
        tokenStream.registerConverter(new ServiceProviderTokenConverter(consumerStore));
    }

    @Override
    public Optional<ServiceProviderToken> get(String token) {
        requireNonNull(token, "token");
        load();
        return ofNullable(tokenMap.get(token));
    }

    @Override
    public Iterable<ServiceProviderToken> getAccessTokensForUser(String username) {
        requireNonNull(username, "username");
        load();
        return tokenMap.values().stream().filter(token -> Objects.equals(username, token.getUser())).collect(toList());
    }

    @Override
    public ServiceProviderToken put(ServiceProviderToken token) {
        requireNonNull(token, "token");
        load();
        tokenMap.put(token.getToken(), token);
        save();
        return token;
    }

    @Override
    public void remove(String token) {
        requireNonNull(token, "token");
        load();
        if (tokenMap.remove(token) != null) {
            save();
        }
    }

    @Override
    public void removeExpiredTokens() {
        load();
        boolean needToSave = false;
        for (ServiceProviderToken token : tokenMap.values()) {
            if (isTokenExpired(token) && tokenMap.remove(token.getToken()) != null) {
                needToSave = true;
            }
        }
        if (needToSave) {
            save();
        }
    }

    @Override
    public void removeExpiredSessions() {
        load();
        boolean needToSave = false;
        for (ServiceProviderToken token : tokenMap.values()) {
            if (isTokenSessionExpired(token) && tokenMap.remove(token.getToken()) != null) {
                needToSave = true;
            }
        }
        if (needToSave) {
            save();
        }
    }

    @Override
    public void removeByConsumer(String consumerKey) {
        requireNonNull(consumerKey, "consumerKey");
        load();
        boolean needToSave = false;
        for (ServiceProviderToken token : tokenMap.values()) {
            Consumer consumer = token.getConsumer();
            if (consumer != null && Objects.equals(consumerKey, consumer.getKey()) &&
                tokenMap.remove(token.getToken()) != null) {
                needToSave = true;
            }
        }
        if (needToSave) {
            save();
        }
    }

    public synchronized void load() {
        if (tokenMap != null) {
            return;
        }

        XmlFile configFile = getConfigFile();
        if (configFile.exists()) {
            try {
                configFile.unmarshal(this);
            } catch (IOException e) {
                log.log(SEVERE, "Failed to load OAuth tokens from disk", e);
                throw new StoreException("Failed to load OAuth tokens", e);
            }
        }
        // tokenMap will be unmarshalled as a HashMap if the config file exists, otherwise will be null. Either
        // way, we convert it to the Jenkins-provided concurrent copy-on-write Map that will be copied on each save and
        // written to disk, until the next Jenkins restart
        tokenMap = new CopyOnWriteMap.Hash<>(tokenMap != null ? tokenMap : emptyMap());
    }

    @Override
    public synchronized void save() {
        if (BulkChange.contains(this)) {
            return;
        }

        try {
            getConfigFile().write(this);
        } catch (IOException e) {
            log.log(SEVERE, "Failed to persist OAuth tokens to disk", e);
            throw new StoreException("Failed to persist OAuth tokens", e);
        }
    }

    /**
     * The file where {@link ServiceProviderToken tokens} are saved
     */
    @VisibleForTesting
    protected XmlFile getConfigFile() {
        return new XmlFile(tokenStream, new File(Jenkins.get().getRootDir(), "oauth-tokens.xml"));
    }

    private static final class ServiceProviderTokenConverter implements Converter {

        private static final String ACCESS_TOKEN = "access-token";
        private static final String TOKEN_VALUE = "token-value";
        private static final String TOKEN_SECRET = "token-secret";
        private static final String CONSUMER_KEY = "consumer-key";
        private static final String PROPERTIES = "properties";
        private static final String AUTHORIZATION = "authorization";
        private static final String USER = "user";
        private static final String VERIFIER = "verifier";
        private static final String TOKEN_CREATION_TIME = "creation-time";
        private static final String TOKEN_TIME_TO_LIVE = "time-to-live";
        private static final String CALLBACK = "callback";
        private static final String SESSION = "session";
        private static final String SESSION_HANDLE = "handle";
        private static final String SESSION_CREATION_TIME = "creation-time";
        private static final String SESSION_LAST_RENEWAL_TIME = "last-renewal-time";
        private static final String SESSION_TIME_TO_LIVE = "time-to-live";

        private final ConsumerStore consumerStore;

        private ServiceProviderTokenConverter(ConsumerStore consumerStore) {
            this.consumerStore = consumerStore;
        }

        @Override
        public boolean canConvert(Class type) {
            return ServiceProviderToken.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            requireNonNull(source, "source");
            if (!(source instanceof ServiceProviderToken)) {
                log.warning(() -> String.format("Cannot marshal source of type '%s'", source.getClass()));
                throw new StoreException("Cannot marshal token: incorrect source type");
            }

            ServiceProviderToken token = (ServiceProviderToken) source;
            addNode(writer, ACCESS_TOKEN, token.isAccessToken());
            String tokenValue = token.getToken();
            addNode(writer, TOKEN_VALUE, encrypt(tokenValue));
            addNode(writer, TOKEN_SECRET, encrypt(token.getTokenSecret()));
            ofNullable(token.getConsumer()).map(Consumer::getKey)
                    .ifPresent(consumerKey -> addNode(writer, CONSUMER_KEY, consumerKey));
            addNode(writer, AUTHORIZATION, token.getAuthorization());
            addNode(writer, USER, token.getUser());
            addNode(writer, VERIFIER, encrypt(token.getVerifier()));
            addNode(writer, CALLBACK, token.getCallback());
            addNode(writer, TOKEN_CREATION_TIME, token.getCreationTime());
            addNode(writer, TOKEN_TIME_TO_LIVE, token.getTimeToLive());
            addProperties(writer, token.getProperties());
            addSession(writer, token.getSession());
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Boolean accessToken = null;
            String tokenValue = null;
            String tokenSecret = null;
            String consumerKey = null;
            Authorization authorization = null;
            String user = null;
            String verifier = null;
            Long creationTime = null;
            Long timeToLive = null;
            URI callback = null;
            Map<String, String> properties = null;
            ServiceProviderToken.Session session = null;

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String name = reader.getNodeName();
                String value = reader.getValue();
                switch (name) {
                    case ACCESS_TOKEN:
                        accessToken = Boolean.valueOf(value);
                        break;
                    case TOKEN_VALUE:
                        tokenValue = decrypt(value);
                        break;
                    case TOKEN_SECRET:
                        tokenSecret = decrypt(value);
                        break;
                    case CONSUMER_KEY:
                        consumerKey = value;
                        break;
                    case CALLBACK:
                        callback = ofNullable(value).map(URI::create).orElse(null);
                        break;
                    case AUTHORIZATION:
                        authorization = Authorization.valueOf(value);
                        break;
                    case USER:
                        user = value;
                        break;
                    case VERIFIER:
                        verifier = decrypt(value);
                        break;
                    case TOKEN_CREATION_TIME:
                        creationTime = Long.valueOf(value);
                        break;
                    case TOKEN_TIME_TO_LIVE:
                        timeToLive = Long.valueOf(value);
                        break;
                    case SESSION:
                        session = unmarshalSession(reader);
                        break;
                    case PROPERTIES:
                        properties = unmarshalProperties(reader);
                        break;
                    default:
                        log.fine(() ->
                                String.format("Unknown token entry node '%s' with value '%s' is ignored.",
                                        name, value));
                }
                reader.moveUp();
            }

            try {
                ServiceProviderToken.ServiceProviderTokenBuilder tokenBuilder = (accessToken ?
                        newAccessToken(tokenValue) :
                        newRequestToken(tokenValue))
                        .tokenSecret(tokenSecret)
                        .callback(callback)
                        .session(session)
                        .properties(properties)
                        .verifier(verifier)
                        .creationTime(creationTime)
                        .timeToLive(timeToLive)
                        .consumer(consumerStore.get(consumerKey));

                if (AUTHORIZED == authorization && user != null) {
                    tokenBuilder.authorizedBy(user);
                }

                return tokenBuilder.build();
            } catch (Exception e) {
                log.log(SEVERE, "Failed to unmarshal tokens", e);
                throw new ConversionException("Failed to unmarshal tokens", e);
            }
        }

        private static void addProperties(HierarchicalStreamWriter writer, @Nullable Map<String, String> properties) {
            if (properties != null) {
                writer.startNode(PROPERTIES);
                properties.forEach((key, value) -> addNode(writer, key, value));
                writer.endNode();
            }
        }

        private static void addSession(HierarchicalStreamWriter writer,
                                       @Nullable ServiceProviderToken.Session session) {
            if (session != null) {
                writer.startNode(SESSION);
                addNode(writer, SESSION_HANDLE, encrypt(session.getHandle()));
                addNode(writer, SESSION_CREATION_TIME, session.getCreationTime());
                addNode(writer, SESSION_LAST_RENEWAL_TIME, session.getLastRenewalTime());
                addNode(writer, SESSION_TIME_TO_LIVE, session.getTimeToLive());
                writer.endNode();
            }
        }

        private static void addNode(HierarchicalStreamWriter writer, String name, Object value) {
            writer.startNode(name);
            writer.setValue(Objects.toString(value));
            writer.endNode();
        }

        private static Map<String, String> unmarshalProperties(HierarchicalStreamReader reader) {
            Map<String, String> properties = new HashMap<>();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                properties.put(reader.getNodeName(), reader.getValue());
                reader.moveUp();
            }
            return properties;
        }

        @Nullable
        private static ServiceProviderToken.Session unmarshalSession(HierarchicalStreamReader reader) {
            String handle = null;
            Long creationTime = null;
            Long lastRenewalTime = null;
            Long timeToLive = null;
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String name = reader.getNodeName();
                String value = reader.getValue();
                switch (name) {
                    case SESSION_HANDLE:
                        handle = decrypt(value);
                        break;
                    case SESSION_CREATION_TIME:
                        creationTime = Long.valueOf(value);
                        break;
                    case SESSION_LAST_RENEWAL_TIME:
                        lastRenewalTime = Long.valueOf(value);
                        break;
                    case SESSION_TIME_TO_LIVE:
                        timeToLive = Long.valueOf(value);
                        break;
                    default:
                        log.fine(() ->
                                String.format("Unknown token session entry node '%s' with value '%s' is ignored.",
                                        name, value));
                }
                reader.moveUp();
            }
            if (handle == null) {
                return null;
            }
            ServiceProviderToken.Session.Builder session = ServiceProviderToken.Session.newSession(handle);
            if (creationTime != null) {
                session.creationTime(creationTime);
            }
            if (lastRenewalTime != null) {
                session.lastRenewalTime(lastRenewalTime);
            }
            if (timeToLive != null) {
                session.timeToLive(timeToLive);
            }
            return session.build();
        }

        @Nullable
        private static String encrypt(@Nullable String unencryptedValue) {
            if (isBlank(unencryptedValue)) {
                return unencryptedValue;
            }
            return Secret.fromString(unencryptedValue).getEncryptedValue();
        }

        @Nullable
        private static String decrypt(@Nullable String encryptedValue) {
            if (isBlank(encryptedValue)) {
                return encryptedValue;
            }
            return Secret.toString(Secret.decrypt(encryptedValue));
        }
    }
}
