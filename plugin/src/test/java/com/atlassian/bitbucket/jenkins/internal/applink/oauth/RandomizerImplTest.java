package com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.RandomizerImpl.ALPHA_NUM_CODEC;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasLength;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class RandomizerImplTest {

    @Parameterized.Parameter
    public int length;

    private RandomizerImpl randomizer;

    @Parameterized.Parameters(name = "length={0}")
    public static Iterable<Integer> data() {
        // 0 + 1 + 10 random values between 2 and 199 + 10 random values between 200 and 1999
        IntStream.Builder intStreamBuilder = IntStream.builder().add(0).add(1);
        for (int i = 0; i < 10; i++) {
            intStreamBuilder.add(RandomUtils.nextInt(2, 200));
        }
        for (int i = 0; i < 10; i++) {
            intStreamBuilder.add(RandomUtils.nextInt(200, 2000));
        }
        return intStreamBuilder.build().boxed().collect(toList());
    }

    @Before
    public void setup() {
        randomizer = new RandomizerImpl();
    }

    @Test
    public void testRandomAlphanumericString() {
        String result = randomizer.randomAlphanumericString(length);

        assertThat(result, hasLength(length));
        assertThat(result.toCharArray(), new ValidCharsMatcher(ALPHA_NUM_CODEC));
    }

    @Test
    public void testRandomString() {
        String result = randomizer.randomAlphanumericString(length);

        assertThat(result, hasLength(length));
        assertThat(result.toCharArray(), new ValidCharsMatcher(ALPHA_NUM_CODEC));
    }

    private static final class ValidCharsMatcher extends TypeSafeDiagnosingMatcher<char[]> {

        private final char[] validChars;

        private ValidCharsMatcher(char[] validChars) {
            this.validChars = validChars;
        }

        @Override
        protected boolean matchesSafely(char[] actual, Description mismatchDescription) {
            Set<Character> invalidChars = new HashSet<>();
            for (char ch : actual) {
                if (!ArrayUtils.contains(validChars, ch)) {
                    invalidChars.add(ch);
                }
            }
            if (!invalidChars.isEmpty()) {
                mismatchDescription.appendText("contains invalid chars: ")
                        .appendValue(invalidChars);
                return false;
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("string containing only these characters: ")
                    .appendValue(validChars);
        }
    }
}
