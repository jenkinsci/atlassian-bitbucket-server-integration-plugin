package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.RSAKeys;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod.HMAC_SHA1;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod.RSA_SHA1;
import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class PersistentServiceProviderConsumerStoreTest {

    private static final KeyPair KEY_PAIR1;
    private static final KeyPair KEY_PAIR2;
    private static final Consumer RSA_CONSUMER;
    private static final Consumer HMAC_CONSUMER;
    private static final Consumer HMAC_CONSUMER_NO_PUBLIC_KEY;

    static {
        try {
            KEY_PAIR1 = RSAKeys.generateKeyPair();
            KEY_PAIR2 = RSAKeys.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        RSA_CONSUMER = Consumer.key("consumer-rsa")
                .name("Consumer using RSA")
                .description("This is a Consumer using RSA signature method")
                .signatureMethod(RSA_SHA1)
                .publicKey(KEY_PAIR1.getPublic())
                .callback(URI.create("http://consumer/callback1"))
                .build();

        HMAC_CONSUMER = Consumer.key("consumer-hmac")
                .name("Consumer using HMAC")
                .description("This is a Consumer using HMAC signature method")
                .signatureMethod(HMAC_SHA1)
                .publicKey(KEY_PAIR2.getPublic())
                .callback(URI.create("http://consumer/callback2"))
                .build();

        HMAC_CONSUMER_NO_PUBLIC_KEY = Consumer.key("consumer-hmac-no-pub-key")
                .name("Consumer using HMAC")
                .description("This is a Consumer using HMAC signature method but no public key")
                .signatureMethod(HMAC_SHA1)
                .callback(URI.create("http://consumer/callback"))
                .build();
    }

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.WARN);

    private Map<String, Consumer> persistedConsumerMap;
    private Map<String, Consumer> inMemoryConsumerMap;

    private PersistentServiceProviderConsumerStore consumerStore;

    @Before
    public void setup() throws IOException {
        // we use this map to simulate persistence: save() copies in-memory map to this map and load() does the opposite
        persistedConsumerMap = new HashMap<>();
        // initial load (to simulate loading persisted consumers on Jenkins startup)
        persistedConsumerMap.put(RSA_CONSUMER.getKey(), RSA_CONSUMER);
        persistedConsumerMap.put(HMAC_CONSUMER.getKey(), HMAC_CONSUMER);
        persistedConsumerMap.put(HMAC_CONSUMER_NO_PUBLIC_KEY.getKey(), HMAC_CONSUMER_NO_PUBLIC_KEY);
        inMemoryConsumerMap = new ConcurrentHashMap<>();
        consumerStore = spy(new PersistentServiceProviderConsumerStore(inMemoryConsumerMap));
        doAnswer(invocation -> {
            inMemoryConsumerMap.clear();
            inMemoryConsumerMap.putAll(persistedConsumerMap);
            return null;
        }).when(consumerStore).load();
        doAnswer(invocation -> {
            persistedConsumerMap.clear();
            persistedConsumerMap.putAll(inMemoryConsumerMap);
            return null;
        }).when(consumerStore).save();
    }

    @Test
    public void testAdd() {
        Consumer newConsumer = Consumer.key("new-consumer-rsa")
                .name("Another Consumer using RSA")
                .description("This is another Consumer also using RSA signature method")
                .signatureMethod(RSA_SHA1)
                .publicKey(KEY_PAIR2.getPublic())
                .callback(URI.create("http://consumer/callback3"))
                .build();

        consumerStore.add(newConsumer);

        assertThat(consumerStore.get("new-consumer-rsa"), optionalWithValue(isConsumer(newConsumer)));
        assertThat(inMemoryConsumerMap, allOf(aMapWithSize(4),
                hasEntry(is(RSA_CONSUMER.getKey()), isConsumer(RSA_CONSUMER)),
                hasEntry(is(HMAC_CONSUMER.getKey()), isConsumer(HMAC_CONSUMER)),
                hasEntry(is(HMAC_CONSUMER_NO_PUBLIC_KEY.getKey()), isConsumer(HMAC_CONSUMER_NO_PUBLIC_KEY)),
                hasEntry(is(newConsumer.getKey()), isConsumer(newConsumer))
        ));
    }

    @Test(expected = StoreException.class)
    public void testAddExistingThrowsException() {
        consumerStore.add(RSA_CONSUMER);
    }

    @Test(expected = NullPointerException.class)
    public void testAddNullInputThrowsException() {
        consumerStore.add(null);
    }

    @Test
    public void testGet() {
        assertThat(consumerStore.get(RSA_CONSUMER.getKey()), optionalWithValue(isConsumer(RSA_CONSUMER)));
        assertThat(consumerStore.get(HMAC_CONSUMER.getKey()), optionalWithValue(isConsumer(HMAC_CONSUMER)));
        assertThat(consumerStore.get(HMAC_CONSUMER_NO_PUBLIC_KEY.getKey()),
                optionalWithValue(isConsumer(HMAC_CONSUMER_NO_PUBLIC_KEY)));
        assertThat(consumerStore.get("non-existent-consumer"), emptyOptional());
    }

    @Test(expected = NullPointerException.class)
    public void testGetNullInputThrowsException() {
        consumerStore.get(null);
    }

    @Test
    public void testGetAll() {
        assertThat(consumerStore.getAll(), containsInAnyOrder(isConsumer(RSA_CONSUMER), isConsumer(HMAC_CONSUMER),
                isConsumer(HMAC_CONSUMER_NO_PUBLIC_KEY)));
    }

    @Test
    public void testDelete() {
        consumerStore.delete(HMAC_CONSUMER.getKey());

        assertThat(consumerStore.get(HMAC_CONSUMER.getKey()), emptyOptional());
        assertThat(inMemoryConsumerMap, allOf(aMapWithSize(2),
                hasEntry(is(RSA_CONSUMER.getKey()), isConsumer(RSA_CONSUMER)),
                hasEntry(is(HMAC_CONSUMER_NO_PUBLIC_KEY.getKey()), isConsumer(HMAC_CONSUMER_NO_PUBLIC_KEY))
        ));
    }

    @Test
    public void testDeleteNonExistentConsumer() {
        consumerStore.delete("non-existent-consumer");

        assertThat(consumerStore.get("non-existent-consumer"), emptyOptional());
        assertThat(inMemoryConsumerMap, allOf(aMapWithSize(3),
                hasEntry(is(RSA_CONSUMER.getKey()), isConsumer(RSA_CONSUMER)),
                hasEntry(is(HMAC_CONSUMER.getKey()), isConsumer(HMAC_CONSUMER)),
                hasEntry(is(HMAC_CONSUMER_NO_PUBLIC_KEY.getKey()), isConsumer(HMAC_CONSUMER_NO_PUBLIC_KEY))
        ));
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteNullInputThrowsException() {
        consumerStore.delete(null);
    }

    @Test
    public void testUpdate() {
        inMemoryConsumerMap.clear();
        persistedConsumerMap.clear();

        Consumer newConsumer = Consumer.key("new-consumer-rsa")
                .name("Another Consumer using RSA")
                .description("This is another Consumer also using RSA signature method")
                .signatureMethod(RSA_SHA1)
                .publicKey(KEY_PAIR2.getPublic())
                .callback(URI.create("http://consumer/callback3"))
                .build();
        consumerStore.add(newConsumer);

        Consumer newConsumerUpdate = Consumer.key("new-consumer-rsa")
                .name("Different")
                .description("No description")
                .signatureMethod(HMAC_SHA1)
                .callback(URI.create("http://consumer/callback3"))
                .build();

        consumerStore.update(newConsumerUpdate);

        assertThat(consumerStore.get("new-consumer-rsa"), optionalWithValue(isConsumer(newConsumerUpdate)));
        assertThat(inMemoryConsumerMap, allOf(aMapWithSize(1),
                hasEntry(is(newConsumerUpdate.getKey()), isConsumer(newConsumerUpdate))
        ));
    }

    @Test(expected = StoreException.class)
    public void testUpdateThrowsExceptionForNonExistingKey() {
        Consumer newConsumerUpdate = Consumer.key("garbage")
                .name("Different")
                .description("No description")
                .signatureMethod(HMAC_SHA1)
                .callback(URI.create("http://consumer/callback3"))
                .build();

        consumerStore.update(newConsumerUpdate);
    }

    private static ConsumerMatcher isConsumer(Consumer consumer) {
        return new ConsumerMatcher(consumer);
    }

    private static class ConsumerMatcher extends TypeSafeDiagnosingMatcher<Consumer> {

        private final Consumer expected;

        private Matcher<String> key;
        private Matcher<String> name;
        private Matcher<Optional<String>> desc;
        private Matcher<Optional<URI>> callback;
        private Matcher<Optional<String>> secret;
        private Matcher<SignatureMethod> signatureMethod;
        private Matcher<Optional<PublicKey>> publicKey;

        private ConsumerMatcher(Consumer expected) {
            this.expected = expected;
            if (expected != null) {
                key = is(expected.getKey());
                name = is(expected.getName());
                desc = is(expected.getDescription());
                callback = is(expected.getCallback());
                secret = is(expected.getConsumerSecret());
                signatureMethod = is(expected.getSignatureMethod());
                publicKey = is(expected.getPublicKey());
            }
        }

        @Override
        protected boolean matchesSafely(Consumer actual, Description description) {
            if (actual == expected) {
                return true;
            }
            if (expected == null) {
                description.appendText("is not null");
                return false;
            }
            if (actual == null) {
                description.appendText("is null");
                return false;
            }
            List<String> mismatchFields = new ArrayList<>();
            List<Matcher<?>> mismatchMatchers = new ArrayList<>();
            List<Object> mismatchValues = new ArrayList<>();
            if (!key.matches(actual.getKey())) {
                mismatchFields.add("key");
                mismatchMatchers.add(key);
                mismatchValues.add(actual.getKey());
            }
            if (!name.matches(actual.getName())) {
                mismatchFields.add("name");
                mismatchMatchers.add(name);
                mismatchValues.add(actual.getName());
            }
            if (!desc.matches(actual.getDescription())) {
                mismatchFields.add("description");
                mismatchMatchers.add(desc);
                mismatchValues.add(actual.getDescription());
            }
            if (!callback.matches(actual.getCallback())) {
                mismatchFields.add("callback");
                mismatchMatchers.add(callback);
                mismatchValues.add(actual.getCallback());
            }
            if (!secret.matches(actual.getConsumerSecret())) {
                mismatchFields.add("consumerSecret");
                mismatchMatchers.add(secret);
                mismatchValues.add(actual.getConsumerSecret());
            }
            if (!signatureMethod.matches(actual.getSignatureMethod())) {
                mismatchFields.add("signatureMethod");
                mismatchMatchers.add(signatureMethod);
                mismatchValues.add(actual.getSignatureMethod());
            }
            if (!publicKey.matches(actual.getPublicKey())) {
                mismatchFields.add("publicKey");
                mismatchMatchers.add(publicKey);
                mismatchValues.add(actual.getPublicKey());
            }
            if (!mismatchFields.isEmpty()) {
                description.appendText("Consumer attributes [")
                        .appendText(String.join(", ", mismatchFields))
                        .appendText("] with expectations ")
                        .appendList("[", ", ", "]", mismatchMatchers)
                        .appendText(" instead had values ")
                        .appendValueList("[", ", ", "]", mismatchValues);
                return false;
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is ").appendValue(expected);
        }
    }
}
