package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider;

import com.google.common.primitives.Chars;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.RandomValueOAuthTokenVerifierGenerator.DEFAULT_CODEC;
import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.RandomValueOAuthTokenVerifierGenerator.VERIFIER_LENGTH_BYTES;
import static org.hamcrest.Matchers.hasLength;
import static org.junit.Assert.assertThat;

public class RandomValueOAuthTokenVerifierGeneratorTest {

    private RandomValueOAuthTokenVerifierGenerator verifierGenerator;

    @Before
    public void setup() {
        verifierGenerator = new RandomValueOAuthTokenVerifierGenerator();
    }

    @Test
    public void testGenerateVerifier() {
        String verifier = verifierGenerator.generateVerifier();
        assertThat(verifier, hasLength(VERIFIER_LENGTH_BYTES));
        assertThat(verifier, containsValidCharacters(DEFAULT_CODEC));
    }

    private static CharacterMatcher containsValidCharacters(char[] validCharacters) {
        return new CharacterMatcher(validCharacters);
    }

    private static final class CharacterMatcher extends TypeSafeDiagnosingMatcher<String> {

        private final char[] validCharacters;

        private CharacterMatcher(char[] validCharacters) {
            this.validCharacters = validCharacters;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("non-blank string containing only characters ").appendValue(validCharacters);
        }

        @Override
        protected boolean matchesSafely(String verifierStr, Description description) {
            Set<Character> invalidChars = new HashSet<>();
            for (char verifierChar : verifierStr.toCharArray()) {
                if (!Chars.contains(validCharacters, verifierChar)) {
                    invalidChars.add(verifierChar);
                }
            }
            if (!invalidChars.isEmpty()) {
                description.appendText("contained these invalid characters: ").appendValue(invalidChars);
                return false;
            }
            return true;
        }
    }
}
