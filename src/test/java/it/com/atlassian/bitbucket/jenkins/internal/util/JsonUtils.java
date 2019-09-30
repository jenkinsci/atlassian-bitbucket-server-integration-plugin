package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JenkinsMirrorListBox {

        private final String listBoxClass;
        private final List<JenkinsMirrorListBoxValues> values;

        @JsonCreator
        public JenkinsMirrorListBox(
                @JsonProperty(value = "_class", required = true) String listBoxClass,
                @JsonProperty(value = "values", required = true) List<JenkinsMirrorListBoxValues> values) {
            this.listBoxClass = listBoxClass;
            this.values = values;
        }

        public List<JenkinsMirrorListBoxValues> getValues() {
            return values;
        }

        public String getListBoxClass() {
            return listBoxClass;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("listBoxClass", listBoxClass)
                    .append("values", values)
                    .toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JenkinsMirrorListBoxValues {

        private String name;
        private String selected;
        private String value;

        public String getName() {
            return name;
        }

        public boolean isSelected() {
            return Boolean.valueOf(selected);
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                    .append("name", name)
                    .append("selected", selected)
                    .append("value", value)
                    .toString();
        }
    }
}
