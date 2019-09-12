package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.trigger.InstanceBasedNameGenerator;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.interfaces.RSAPublicKey;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstanceBasedNameGeneratorTest {

    private final String key = "key";
    private InstanceBasedNameGenerator generator;
    @Mock
    private InstanceIdentity instanceIdentity;
    @Mock
    private RSAPublicKey publicKey;

    @Before
    public void setup() {
        when(instanceIdentity.getPublic()).thenReturn(publicKey);
        when(publicKey.getEncoded()).thenReturn(key.getBytes());
        generator = new InstanceBasedNameGenerator(instanceIdentity);
    }

    @Test
    public void testSameNameAcrossInvocation() {
        String str = generator.getUniqueName();

        assertThat(str, is(equalTo(generator.getUniqueName())));
    }

    @Test
    public void testDifferentUniqueNameForDifferentKeys() {
        String str = generator.getUniqueName();
        when(publicKey.getEncoded()).thenReturn(key.toUpperCase().getBytes());
        generator = new InstanceBasedNameGenerator(instanceIdentity);

        assertThat(str, is(not(equalTo(generator.getUniqueName()))));
    }
}
