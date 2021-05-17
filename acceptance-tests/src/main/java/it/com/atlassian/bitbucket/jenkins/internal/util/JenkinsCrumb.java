package it.com.atlassian.bitbucket.jenkins.internal.util;

import io.restassured.http.Header;

public class JenkinsCrumb {

    private String crumb;
    private String crumbRequestField;

    public JenkinsCrumb() {

    }

    public String getCrumb() {
        return crumb;
    }

    public void setCrumb(String crumb) {
        this.crumb = crumb;
    }

    public Header getCrumbHeader() {
        return new Header(crumbRequestField, crumb);
    }

    public String getCrumbRequestField() {
        return crumbRequestField;
    }

    public void setCrumbRequestField(String crumbRequestField) {
        this.crumbRequestField = crumbRequestField;
    }
}
