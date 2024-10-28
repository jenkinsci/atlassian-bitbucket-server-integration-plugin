package com.atlassian.bitbucket.jenkins.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketValueProperty {
        private final String id;
        private final String displayId;
        private final long committerTimestamp;
        private final String message;

        @JsonCreator
        public BitbucketValueProperty(@JsonProperty("id") String id,
                     @JsonProperty("displayId") String displayId,
                     @JsonProperty("committerTimestamp") long committerTimestamp,
                     @JsonProperty("message") String message) {
            this.id = requireNonNull(id, "id");
            this.displayId = requireNonNull(displayId, "displayId");
            this.message = requireNonNull(message, "message");
            this.committerTimestamp = requireNonNull(committerTimestamp, "committerTimestamp");
        }

        public final long getCommitterTimestamp() {
            return committerTimestamp;
        }

        public String getDisplayId() {
            return displayId;
        }

        public String getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }

    }
