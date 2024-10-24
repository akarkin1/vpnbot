package org.github.akarkin1.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CfnStackOutputParameter(@JsonProperty("OutputKey") String outputKey,
                                      @JsonProperty("OutputValue") String outputValue) {

}
