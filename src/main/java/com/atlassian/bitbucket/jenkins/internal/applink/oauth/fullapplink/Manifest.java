package com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.List;

enum TypeId {
    GENERIC("generic"),
    JENKINS("jenkins");

    private final String value;

    TypeId(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

@XStreamAlias("manifest")
public class Manifest {

    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    private final String applinksVersion = "7.2.0";
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    private final String buildNumber = "201001";
    private final String iconUri;
    private final String iconUrl;
    private final String id;
    @XStreamImplicit(itemFieldName = "incomingAuthenticationType")
    private final List incomingAuthenticationType = Collections.singletonList(
            "com.atlassian.applinks.api.auth.types.OAuthAuthenticationProvider"
    );
    private final String name;
    @XStreamImplicit(itemFieldName = "outgoingAuthenticationType")
    private final List outgoingAuthenticationType = Collections.emptyList();

    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    private final boolean publicSignup = false;
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    private final String typeId = TypeId.JENKINS.getValue();
    private final String url;
    @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
    private final String version = "2.1.1";

    public Manifest(String id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.iconUrl = url + "/static/9c75a25f/images/svgs/logo.svg";
        this.iconUri = url + "/static/9c75a25f/images/svgs/logo.svg";
    }

    public String getApplinksVersion() {
        return applinksVersion;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getIconUri() {
        return iconUri;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getId() {
        return id;
    }

    public List<String> getIncomingAuthenticationTypes() {
        return incomingAuthenticationType;
    }

    public String getName() {
        return name;
    }

    public List<String> getOutgoingAuthenticationTypes() {
        return outgoingAuthenticationType;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getUrl() {
        return url;
    }

    public String getVersion() {
        return version;
    }

    public boolean isPublicSignup() {
        return publicSignup;
    }
}