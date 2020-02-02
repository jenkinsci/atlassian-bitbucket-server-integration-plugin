package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.temp;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Singleton
public class ServiceProviderTokenStoreImpl implements ServiceProviderTokenStore {

    private Map<String, ServiceProviderToken> tokens = new ConcurrentHashMap<>();

    @Override
    public Optional<ServiceProviderToken> get(String token) throws StoreException {
        return ofNullable(tokens.get(token));
    }

    @Override
    public Iterable<ServiceProviderToken> getAccessTokensForUser(String username) {
        return tokens.values().stream().filter(token -> token.getUser().getName().equals(username)).collect(toList());
    }

    @Override
    public ServiceProviderToken put(ServiceProviderToken token) throws StoreException {
        tokens.put(token.getToken(), token);
        return token;
    }

    @Override
    public void remove(String token) throws StoreException {
        tokens.remove(token);
    }

    @Override
    public void removeExpiredTokens() throws StoreException {

    }

    @Override
    public void removeExpiredSessions() throws StoreException {

    }

    @Override
    public void removeByConsumer(String consumerKey) {

    }
}
