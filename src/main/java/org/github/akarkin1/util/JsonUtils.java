package org.github.akarkin1.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static <T> String toJson(T obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize object to json: %s".formatted(obj), e);
    }
  }

  public static <T> T parseJson(String json, TypeReference<T> returnType) {
    try {
      return MAPPER.readValue(json, returnType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to parse json: %s".formatted(json), e);
    }
  }

}
