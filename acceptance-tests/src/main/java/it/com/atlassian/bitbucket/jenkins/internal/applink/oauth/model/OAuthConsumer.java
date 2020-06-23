package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OAuthConsumer {

    private final String key;
    private final String name;
    private final String secret;
    private final String callback;

    @JsonCreator
    public OAuthConsumer(@JsonProperty("consumerKey") String key,
                         @JsonProperty("consumerName") String name,
                         @JsonProperty("consumerSecret") String secret,
                         @JsonProperty("callbackUrl") String callback) {
        this.key = key;
        this.name = name;
        this.secret = secret;
        this.callback = callback;
    }

    @JsonGetter("consumerKey")
    public String getKey() {
        return key;
    }

    @JsonGetter("consumerName")
    public String getName() {
        return name;
    }

    @JsonGetter("consumerSecret")
    public String getSecret() {
        return secret;
    }

    @JsonGetter("callbackUrl")
    public String getCallback() {
        return callback;
    }
}
