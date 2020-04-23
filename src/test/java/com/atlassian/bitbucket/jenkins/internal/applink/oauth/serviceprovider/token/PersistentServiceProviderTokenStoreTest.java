package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.Session.newSession;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newAccessToken;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderToken.newRequestToken;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.util.TestData.Consumers.RSA_CONSUMER_WITH_2LO;
import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.*;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class PersistentServiceProviderTokenStoreTest {

    private static final ServiceProviderToken REQUEST_TOKEN_1 = newRequestToken("req-token1")
            .callback(URI.create("http://some-callback-url/endpoint1"))
            .consumer(RSA_CONSUMER)
            .creationTime(currentTimeMillis())
            .timeToLive(ofDays(1L).toMillis())
            .tokenSecret("the-secret-token1")
            .authorizedBy("test-user1")
            .verifier("abc123")
            .build();

    private static final ServiceProviderToken REQUEST_TOKEN_2 = newRequestToken("req-token2")
            .callback(URI.create("http://some-callback-url/endpoint2"))
            .consumer(RSA_CONSUMER_WITH_2LO)
            .creationTime(currentTimeMillis())
            .timeToLive(ofDays(1L).toMillis())
            .tokenSecret("the-secret-token2")
            .authorizedBy("test-user2")
            .verifier("def456")
            .build();

    private static final ServiceProviderToken ACCESS_TOKEN_1 = newAccessToken("access-token1")
            .callback(URI.create("http://some-callback-url/endpoint1"))
            .consumer(RSA_CONSUMER)
            .creationTime(currentTimeMillis())
            .timeToLive(ofDays(1L).toMillis())
            .tokenSecret("the-secret-token3")
            .authorizedBy("test-user2")
            .session(newSession("session1")
                    .creationTime(currentTimeMillis())
                    .timeToLive(ofHours(1L).toMillis())
                    .lastRenewalTime(currentTimeMillis())
                    .build())
            .verifier("xyz789")
            .build();

    private static final ServiceProviderToken ACCESS_TOKEN_2 = newAccessToken("access-token2")
            .callback(URI.create("http://some-callback-url/endpoint3"))
            .consumer(RSA_CONSUMER_WITH_2LO)
            .creationTime(currentTimeMillis())
            .timeToLive(ofDays(1L).toMillis())
            .tokenSecret("the-secret-token4")
            .authorizedBy("test-user3")
            .session(newSession("session2")
                    .creationTime(currentTimeMillis())
                    .timeToLive(ofHours(1L).toMillis())
                    .lastRenewalTime(currentTimeMillis())
                    .build())
            .verifier("mno567")
            .build();

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.WARN);

    @Mock
    private ServiceProviderConsumerStore consumerStore;

    private Map<String, ServiceProviderToken> persistedTokenMap;
    private Map<String, ServiceProviderToken> inMemoryTokenMap;

    private PersistentServiceProviderTokenStore tokenStore;

    @Before
    public void setUp() {
        // we use this map to simulate persistence: save() copies in-memory map to this map and load() does the opposite
        persistedTokenMap = new HashMap<>();
        // initial load (to simulate loading persisted tokens on Jenkins startup)
        persistedTokenMap.put(REQUEST_TOKEN_1.getToken(), REQUEST_TOKEN_1);
        persistedTokenMap.put(REQUEST_TOKEN_2.getToken(), REQUEST_TOKEN_2);
        persistedTokenMap.put(ACCESS_TOKEN_1.getToken(), ACCESS_TOKEN_1);
        persistedTokenMap.put(ACCESS_TOKEN_2.getToken(), ACCESS_TOKEN_2);
        inMemoryTokenMap = new ConcurrentHashMap<>();
        when(consumerStore.get(RSA_CONSUMER.getKey())).thenReturn(Optional.of(RSA_CONSUMER));
        when(consumerStore.get(RSA_CONSUMER_WITH_2LO.getKey())).thenReturn(Optional.of(RSA_CONSUMER_WITH_2LO));
        tokenStore = spy(new PersistentServiceProviderTokenStore(consumerStore, inMemoryTokenMap));
        doAnswer(invocation -> {
            inMemoryTokenMap.clear();
            inMemoryTokenMap.putAll(persistedTokenMap);
            return null;
        }).when(tokenStore).load();
        doAnswer(invocation -> {
            persistedTokenMap.clear();
            persistedTokenMap.putAll(inMemoryTokenMap);
            return null;
        }).when(tokenStore).save();
    }

    @Test
    public void testGet() {
        assertThat(tokenStore.get(REQUEST_TOKEN_2.getToken()), optionalWithValue(requestToken(REQUEST_TOKEN_2)));
        assertThat(tokenStore.get(ACCESS_TOKEN_1.getToken()), optionalWithValue(accessToken(ACCESS_TOKEN_1)));
        assertThat(tokenStore.get("some-non-existent-token"), emptyOptional());
    }

    @Test
    public void testGetAccessTokensForUser() {
        assertThat(tokenStore.getAccessTokensForUser("test-user2"),
                containsInAnyOrder(requestToken(REQUEST_TOKEN_2), accessToken(ACCESS_TOKEN_1)));
        assertThat(tokenStore.getAccessTokensForUser("test-user3"), contains(accessToken(ACCESS_TOKEN_2)));
        assertThat(tokenStore.getAccessTokensForUser("some-random-user"), emptyIterable());
    }

    @Test
    public void testPut() {
        ServiceProviderToken accessToken = newAccessToken("access-token")
                .callback(URI.create("http://some-callback-url/endpoint"))
                .consumer(RSA_CONSUMER)
                .creationTime(currentTimeMillis())
                .timeToLive(ofDays(1L).toMillis())
                .tokenSecret("the-secret-token")
                .authorizedBy("test-user")
                .session(newSession("session")
                        .creationTime(currentTimeMillis())
                        .timeToLive(ofHours(1L).toMillis())
                        .lastRenewalTime(currentTimeMillis())
                        .build())
                .verifier("zzz789")
                .build();

        assertThat(tokenStore.put(accessToken), accessToken(accessToken));

        assertThat(tokenStore.get(accessToken.getToken()), optionalWithValue(accessToken(accessToken)));
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(5),
                hasEntry(is(REQUEST_TOKEN_1.getToken()), requestToken(REQUEST_TOKEN_1)),
                hasEntry(is(REQUEST_TOKEN_2.getToken()), requestToken(REQUEST_TOKEN_2)),
                hasEntry(is(ACCESS_TOKEN_1.getToken()), accessToken(ACCESS_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2)),
                hasEntry(is(accessToken.getToken()), accessToken(accessToken))));
    }

    @Test
    public void testRemove() {
        assertThat(tokenStore.get(REQUEST_TOKEN_2.getToken()), optionalWithValue(requestToken(REQUEST_TOKEN_2)));

        tokenStore.remove(REQUEST_TOKEN_2.getToken());

        assertThat(tokenStore.get(REQUEST_TOKEN_2.getToken()), emptyOptional());
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(3),
                hasEntry(is(REQUEST_TOKEN_1.getToken()), requestToken(REQUEST_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_1.getToken()), accessToken(ACCESS_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2))));
    }

    @Test
    public void testRemoveNonExistentToken() {
        String token = "non-existent-token";
        assertThat(tokenStore.get(token), emptyOptional());
        tokenStore.remove(token);
        assertThat(tokenStore.get(token), emptyOptional());
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(4),
                hasEntry(is(REQUEST_TOKEN_1.getToken()), requestToken(REQUEST_TOKEN_1)),
                hasEntry(is(REQUEST_TOKEN_2.getToken()), requestToken(REQUEST_TOKEN_2)),
                hasEntry(is(ACCESS_TOKEN_1.getToken()), accessToken(ACCESS_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2))));
    }

    @Test
    public void testRemoveExpiredTokens() {
        long creationTime = currentTimeMillis() - ofMinutes(5L).toMillis();
        long timeToLive = ofMinutes(2L).toMillis();
        ServiceProviderToken expiredToken = newRequestToken("expired-token")
                .callback(URI.create("http://some-callback-url/endpoint"))
                .consumer(RSA_CONSUMER)
                .creationTime(creationTime)
                .timeToLive(timeToLive)
                .tokenSecret("some-random-secret")
                .authorizedBy("test-user4")
                .verifier("abc123")
                .build();
        tokenStore.put(expiredToken);
        assertThat(tokenStore.get(expiredToken.getToken()), optionalWithValue(requestToken(expiredToken)));

        tokenStore.removeExpiredTokens();

        assertThat(tokenStore.get(expiredToken.getToken()), emptyOptional());
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(4),
                hasEntry(is(REQUEST_TOKEN_1.getToken()), requestToken(REQUEST_TOKEN_1)),
                hasEntry(is(REQUEST_TOKEN_2.getToken()), requestToken(REQUEST_TOKEN_2)),
                hasEntry(is(ACCESS_TOKEN_1.getToken()), accessToken(ACCESS_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2))));
    }

    @Test
    public void testRemoveExpiredSessions() {
        long now = currentTimeMillis();
        long sessionCreationTime = now - ofMinutes(10L).toMillis();
        long sessionLastRenewalTime = now - ofMinutes(5L).toMillis();
        long sessionTimeToLive = ofMinutes(2L).toMillis();
        ServiceProviderToken tokenWithExpiredSession = newAccessToken("token-with-expired-session")
                .callback(URI.create("http://some-callback-url/endpoint"))
                .consumer(RSA_CONSUMER)
                .creationTime(currentTimeMillis())
                .timeToLive(ofDays(2L).toMillis())
                .tokenSecret("some-random-secret")
                .authorizedBy("test-user4")
                .verifier("abc123")
                .session(newSession("expired-session")
                        .creationTime(sessionCreationTime)
                        .lastRenewalTime(sessionLastRenewalTime)
                        .timeToLive(sessionTimeToLive)
                        .build())
                .build();
        tokenStore.put(tokenWithExpiredSession);
        assertThat(tokenStore.get(tokenWithExpiredSession.getToken()),
                optionalWithValue(accessToken(tokenWithExpiredSession)));

        tokenStore.removeExpiredSessions();

        assertThat(tokenStore.get(tokenWithExpiredSession.getToken()), emptyOptional());
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(4),
                hasEntry(is(REQUEST_TOKEN_1.getToken()), requestToken(REQUEST_TOKEN_1)),
                hasEntry(is(REQUEST_TOKEN_2.getToken()), requestToken(REQUEST_TOKEN_2)),
                hasEntry(is(ACCESS_TOKEN_1.getToken()), accessToken(ACCESS_TOKEN_1)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2))));
    }

    public void testRemoveByConsumer() {
        tokenStore.removeByConsumer(RSA_CONSUMER.getKey());

        assertThat(tokenStore.get(REQUEST_TOKEN_1.getToken()), emptyOptional());
        assertThat(tokenStore.get(ACCESS_TOKEN_1.getToken()), emptyOptional());
        assertThat(inMemoryTokenMap, allOf(aMapWithSize(2),
                hasEntry(is(REQUEST_TOKEN_2.getToken()), requestToken(REQUEST_TOKEN_2)),
                hasEntry(is(ACCESS_TOKEN_2.getToken()), accessToken(ACCESS_TOKEN_2))));
    }

    private static TokenMatcher accessToken(ServiceProviderToken token) {
        return new TokenMatcher(true, token.getCallback(),
                ofNullable(token.getConsumer()).map(Consumer::getKey).orElse(null), token.getTokenSecret(),
                token.getCreationTime(), token.getTimeToLive(), token.getUser(), token.getVerifier(),
                token.getSession());
    }

    private static TokenMatcher requestToken(ServiceProviderToken token) {
        return new TokenMatcher(false, token.getCallback(),
                ofNullable(token.getConsumer()).map(Consumer::getKey).orElse(null), token.getTokenSecret(),
                token.getCreationTime(), token.getTimeToLive(), token.getUser(), token.getVerifier(),
                token.getSession());
    }

    private static final class TokenMatcher extends TypeSafeDiagnosingMatcher<ServiceProviderToken> {

        private final boolean accessToken;
        private final String secret;
        private final URI callback;
        private final String consumerKey;
        private final long creationTime;
        private final long timeToLive;
        private final String user;
        private final String verifier;
        private final ServiceProviderToken.Session session;

        private TokenMatcher(boolean accessToken, URI callback, String consumerKey, String secret, long creationTime,
                             long timeToLive, String user, String verifier, ServiceProviderToken.Session session) {
            this.callback = callback;
            this.consumerKey = consumerKey;
            this.accessToken = accessToken;
            this.secret = secret;
            this.creationTime = creationTime;
            this.timeToLive = timeToLive;
            this.user = user;
            this.verifier = verifier;
            this.session = session;
        }

        private static void describeSession(Description description, ServiceProviderToken.Session session) {
            if (session == null) {
                description.appendText(", session is null");
            } else {
                description.appendText(", session.handle=")
                        .appendValue(session.getHandle())
                        .appendText(", session.creationTime=")
                        .appendValue(session.getCreationTime())
                        .appendText(", session.timeToLive=")
                        .appendValue(session.getTimeToLive())
                        .appendText(", session.lastRenewalTime")
                        .appendValue(session.getLastRenewalTime());
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("token [type=")
                    .appendValue(accessToken ? "ACCESS" : "REQUEST")
                    .appendText(", callback=")
                    .appendValue(callback)
                    .appendText(", consumer.key=")
                    .appendValue(consumerKey)
                    .appendText(", secret=")
                    .appendValue(secret)
                    .appendText(", creationTime=")
                    .appendValue(creationTime)
                    .appendText(", timeToLive=")
                    .appendValue(timeToLive)
                    .appendText(", authorizedBy(user)=")
                    .appendValue(user)
                    .appendText(", verifier=")
                    .appendValue(verifier);
            describeSession(description, session);
            description.appendText(", token value is not blank]");
        }

        @Override
        protected boolean matchesSafely(ServiceProviderToken token, Description description) {
            if (token == null) {
                description.appendText("is null");
                return false;
            }
            if (!((accessToken ? token.isAccessToken() : token.isRequestToken()) &&
                  Objects.equals(callback, token.getCallback()) &&
                  Objects.equals(secret, token.getTokenSecret()) &&
                  token.getConsumer() != null &&
                  Objects.equals(consumerKey, token.getConsumer().getKey()) &&
                  creationTime == token.getCreationTime() &&
                  timeToLive == token.getTimeToLive() &&
                  Objects.equals(verifier, token.getVerifier()) &&
                  StringUtils.equalsIgnoreCase(user, token.getUser()) &&
                  isNotBlank(token.getToken()))) {
                description.appendText("token [type=")
                        .appendValue(token.isAccessToken() ? "ACCESS" : "REQUEST")
                        .appendText(", callbackUrl=")
                        .appendValue(callback)
                        .appendText(", secret=")
                        .appendValue(token.getTokenSecret())
                        .appendText(", creationTime=")
                        .appendValue(token.getCreationTime())
                        .appendText(", timeToLive=")
                        .appendValue(token.getTimeToLive())
                        .appendText(", authorizedBy(user)=")
                        .appendValue(token.getUser())
                        .appendText(", verifier=")
                        .appendValue(token.getVerifier())
                        .appendText(", token=")
                        .appendValue(token.getToken());
                if (token.getConsumer() == null) {
                    description.appendText(", consumer is null");
                } else {
                    description.appendText(", consumer.key=")
                            .appendValue(token.getConsumer().getKey());
                }
                describeSession(description, token.getSession());
                description.appendText("]");
                return false;
            }
            return true;
        }
    }
}
