package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A BitbucketPage for deserializing the contents of text files
 * @since 3.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketFilePage extends BitbucketPage<String> {

    private List<String> lines = new ArrayList<>();

    @JsonDeserialize(using = BitbucketFilePage.BitbucketFilePageDeserializer.class)
    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
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
