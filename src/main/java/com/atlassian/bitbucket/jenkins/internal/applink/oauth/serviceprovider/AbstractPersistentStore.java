package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.google.common.annotations.VisibleForTesting;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.extended.NamedMapConverter;
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
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.SEVERE;
import static org.apache.commons.lang3.StringUtils.isBlank;

public abstract class AbstractPersistentStore<T> implements Saveable, OnMaster {

    private static final Logger log = Logger.getLogger(AbstractPersistentStore.class.getName());

    private static final int PRIORITY = 100;

    @VisibleForTesting
    protected final transient XStream2 xStream;

    @CopyOnWrite
    protected volatile Map<String, T> entityMap;

    private final String configFileName;

    protected AbstractPersistentStore(String configFileName, Converter entityConverter) {
        this.configFileName = requireNonNull(configFileName, "configFileName");
        xStream = new XStream2();
        xStream.registerConverter(new NamedMapConverter(xStream.getMapper(), getStoreEntryName(),
                getStoreKeyName(), String.class, getStoreValueName(), getEntityClass()), PRIORITY);
        xStream.registerConverter(entityConverter);
    }

    public synchronized void load() {
        if (entityMap != null) {
            return;
        }

        XmlFile configFile = getConfigFile();
        if (configFile.exists()) {
            try {
                configFile.unmarshal(this);
            } catch (IOException e) {
                log.log(SEVERE, "Failed to load items from disk", e);
                throw new StoreException("Failed to load from disk", e);
            }
        }
        // storedMap will be unmarshalled as a HashMap if the config file exists, otherwise will be null. Either
        // way, we convert it to the Jenkins-provided concurrent copy-on-write Map that will be copied on each save and
        // written to disk, until the next Jenkins restart
        entityMap = new CopyOnWriteMap.Hash<>(entityMap != null ? entityMap : emptyMap());
    }

    @Override
    public synchronized void save() {
        if (BulkChange.contains(this)) {
            return;
        }

        try {
            getConfigFile().write(this);
        } catch (IOException e) {
            log.log(SEVERE, "Failed to persist items to disk", e);
            throw new StoreException("Failed to persist to disk", e);
        }
    }

    /**
     * The file where {@link ServiceProviderToken tokens} are saved
     */
    @VisibleForTesting
    protected XmlFile getConfigFile() {
        return new XmlFile(xStream, new File(Jenkins.get().getRootDir(), configFileName));
    }

    @Nullable
    protected static String encrypt(@Nullable String unencryptedValue) {
        if (isBlank(unencryptedValue)) {
            return unencryptedValue;
        }
        return Secret.fromString(unencryptedValue).getEncryptedValue();
    }

    @Nullable
    protected static String decrypt(@Nullable String encryptedValue) {
        if (isBlank(encryptedValue)) {
            return encryptedValue;
        }
        return Secret.toString(Secret.decrypt(encryptedValue));
    }

    protected abstract Class<T> getEntityClass();

    protected abstract String getStoreValueName();

    protected abstract String getStoreKeyName();

    protected abstract String getStoreEntryName();
}
