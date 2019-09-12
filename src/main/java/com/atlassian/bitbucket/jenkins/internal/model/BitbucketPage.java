package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * Basic implementation of a page as returned by all paged resources in Bitbucket Server.
 *
 * @param <T> the entity being paged
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPage<T> {

    private boolean lastPage;
    private int limit;
    private int size;
    private int start;
    private List<T> values;
    private int nextPageStart;

    public int getLimit() {
        return limit;
    }

    public int getNextPageStart() {
        return nextPageStart;
    }

    public void setNextPageStart(int nextPageStart) {
        this.nextPageStart = nextPageStart;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public List<T> getValues() {
        return values;
    }

    public void setValues(List<T> users) {
        values = users;
    }

    public boolean isLastPage() {
        return lastPage;
    }

    @JsonProperty("isLastPage")
    public void setLastPage(boolean lastPage) {
        this.lastPage = lastPage;
    }

    public <E> BitbucketPage<E> transform(Function<? super T, ? extends E> transformFunction) {
        List<E> list = values.stream()
                .map(transformFunction)
                .collect(toList());

        BitbucketPage<E> page = new BitbucketPage<>();
        page.setValues(list);
        page.setLimit(limit);
        page.setSize(size);
        page.setLastPage(lastPage);
        page.setStart(start);
        return page;
    }
}
