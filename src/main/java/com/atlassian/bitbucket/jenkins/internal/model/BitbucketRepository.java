package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketRepository {

    private final String name;
    private final BitbucketProject project;
    private final String slug;
    private final RepositoryState state;
    private List<BitbucketNamedLink> cloneUrls = new ArrayList<>();
    private String selfLink;

    @JsonCreator
    public BitbucketRepository(
            @JsonProperty("name") String name,
            @CheckForNull @JsonProperty("links") Map<String, List<BitbucketNamedLink>> links,
            @JsonProperty("project") BitbucketProject project,
            @JsonProperty("slug") String slug,
            @JsonProperty("state") RepositoryState state) {
        this.name = name;
        this.project = project;
        this.slug = slug;
        this.state = state;
        if (links != null) {
            setLinks(links);
        }
    }

    public BitbucketRepository(String name, BitbucketProject project, String slug, RepositoryState state,
                               List<BitbucketNamedLink> cloneUrls, String selfLink) {
        this.name = name;
        this.project = project;
        this.slug = slug;
        this.state = state;
        this.cloneUrls = cloneUrls;
        this.selfLink = selfLink;
    }

    public List<BitbucketNamedLink> getCloneUrls() {
        return cloneUrls;
    }

    public String getName() {
        return name;
    }

    public BitbucketProject getProject() {
        return project;
    }

    public String getSelfLink() {
        return stripToEmpty(selfLink);
    }

    public String getSlug() {
        return slug;
    }

    public RepositoryState getState() {
        return state;
    }

    private void setLinks(Map<String, List<BitbucketNamedLink>> rawLinks) {
        List<BitbucketNamedLink> clones = rawLinks.get("clone");
        if (clones != null) {
            cloneUrls = unmodifiableList(clones);
        } else {
            cloneUrls = emptyList();
        }
        List<BitbucketNamedLink> link = rawLinks.get("self");
        if (link != null && !link.isEmpty()) { // there should always be exactly one self link.
            selfLink = link.get(0).getHref();
        }
    }
}
