package hudson.util;

/**
 * Utility class to work around testing limitations in Jenkins in regards to Secret.
 */
public class SecretFactory {

    private SecretFactory() {
    }

    /**
     * Return a new {@link Secret} with the provided string as the <em>unencrypted</em> value. The
     * returned secret will <em>not</em> have the encrypted bytes set.
     *
     * @param secret
     * @return
     */
    public static Secret getSecret(String secret) {
        return new Secret(secret);
    }
}
