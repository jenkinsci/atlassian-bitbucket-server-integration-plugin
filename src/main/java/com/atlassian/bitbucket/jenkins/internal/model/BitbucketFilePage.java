package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Basic implementation of a page as returned by all paged resources in Bitbucket Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketFilePage extends BitbucketPage<String> {

    private boolean lastPage;
    private int limit;
    private List<String> lines = new ArrayList<>();
    private int nextPageStart;
    private int size;
    private int start;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    @JsonDeserialize(using = BitbucketFilePage.BitbucketFilePageDeserializer.class)
    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public int getNextPageStart() {
        return nextPageStart;
    }

    public void setNextPageStart(int nextPageStart) {
        this.nextPageStart = nextPageStart;
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

    public boolean isLastPage() {
        return lastPage;
    }

    @JsonProperty("isLastPage")
    public void setLastPage(boolean lastPage) {
        this.lastPage = lastPage;
    }

    static class BitbucketFilePageDeserializer extends JsonDeserializer {

        @Override
        public Object deserialize(JsonParser jsonParser,
                                  DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            List<Map<String, String>> pageResult = jsonParser.readValueAs(List.class);
            return pageResult.stream().map(map -> map.get("text")).collect(Collectors.toList());
        }
    }
}
