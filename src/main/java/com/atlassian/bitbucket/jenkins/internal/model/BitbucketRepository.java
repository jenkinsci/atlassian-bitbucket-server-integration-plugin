package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketRepository {

    private final int id;
    private final String name;
    private final BitbucketProject project;
    private final String slug;
    private final RepositoryState state;
    private List<BitbucketNamedLink> cloneUrls = new ArrayList<>();
    private String selfLink;

    public BitbucketRepository(int repositoryId, String name, BitbucketProject project, String slug,
                               RepositoryState state,
                               List<BitbucketNamedLink> cloneUrls, String selfLink) {
        id = repositoryId;
        this.name = name;
        this.project = project;
        this.slug = slug;
        this.state = state;
        this.cloneUrls = cloneUrls;
        this.selfLink = selfLink;
    }

    @JsonCreator
    public BitbucketRepository(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @CheckForNull @JsonProperty("links") Map<String, List<BitbucketNamedLink>> links,
            @JsonProperty("project") BitbucketProject project,
            @JsonProperty("slug") String slug,
            @JsonProperty("state") RepositoryState state) {
        this.id = id;
        this.name = name;
        this.project = project;
        this.slug = slug;
        this.state = state;
        if (links != null) {
            setLinks(links);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitbucketRepository that = (BitbucketRepository) o;
        return id == that.id &&
               Objects.equals(name, that.name) &&
               Objects.equals(project, that.project) &&
               Objects.equals(slug, that.slug) &&
               state == that.state &&
               Objects.equals(cloneUrls, that.cloneUrls) &&
               Objects.equals(selfLink, that.selfLink);
    }

    public List<BitbucketNamedLink> getCloneUrls() {
        return cloneUrls;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BitbucketProject getProject() {
        return project;
    }

    /**
     * The self link on webhook events was only introduced in Bitbucket Server 5.14, so this may be blank
     *
     * @return the self link for the repository if the Bitbucket instance is 5.14 or higher, otherwise {@code ""}
     */
    public String getSelfLink() {
        return stripToEmpty(selfLink);
    }

    public String getSlug() {
        return slug;
    }

    public RepositoryState getState() {
        return state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, project, slug, state, cloneUrls, selfLink);
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
