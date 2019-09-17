package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public class JsonUtils {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HudsonResponse<T> {

        private T data;
        private String status;

        public T getData() {
            return data;
        }

        public String getStatus() {
            return status;
        }
    }

    // We wrap the actual BitbucketProject response and hide the 'links' field behind a 'selfLink' field so the
    // response from Jenkins is actually different to the one from Bitbucket
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JenkinsBitbucketProject {

        private String key;
        private String name;
        private String selfLink;

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }

        public String getSelfLink() {
            return selfLink;
        }
    }

    // We wrap the actual BitbucketRepository response and hide the 'links' field behind a 'cloneUrls' field so the
    // response from Jenkins is actually different to the one from Bitbucket
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JenkinsBitbucketRepository {

        private List<BitbucketNamedLink> cloneUrls;
        private String name;
        private JenkinsBitbucketProject project;
        private String slug;

        public List<BitbucketNamedLink> getCloneUrls() {
            return cloneUrls;
        }

        public String getName() {
            return name;
        }

        public JenkinsBitbucketProject getProject() {
            return project;
        }

        public String getSlug() {
            return slug;
        }
    }
}
