package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public record NodeRef(String regionId, String taskId) {

  private static final String SEPARATOR = ":";

  public NodeRef {
    if (!isValidPart(regionId) || !isValidPart(taskId)) {
      throw new IllegalArgumentException("Invalid node reference: regionId=%s, taskId=%s".formatted(regionId, taskId));
    }
  }

  public String encode() {
    return regionId + SEPARATOR + taskId;
  }

  public static Optional<NodeRef> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String[] parts = value.split(SEPARATOR, -1);
    if (parts.length != 2 || !isValidPart(parts[0]) || !isValidPart(parts[1])) {
      return Optional.empty();
    }
    return Optional.of(new NodeRef(parts[0], parts[1]));
  }

  private static boolean isValidPart(String part) {
    return StringUtils.isNotBlank(part) && !part.contains(SEPARATOR);
  }

}
