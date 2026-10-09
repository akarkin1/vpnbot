package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public record UiAction(Type type, String arg) {

  private static final int MAX_DATA_BYTES = 64;
  private static final String SEPARATOR = ":";

  public enum Type { HOME, HELP, RUN, RUN_NEW, USE, STOP, STOP_CONFIRM }

  public UiAction {
    if (!isValid(type, arg)) {
      throw new IllegalArgumentException("Invalid UI action: type=%s, arg=%s".formatted(type, arg));
    }
  }

  public static UiAction home() {
    return new UiAction(Type.HOME, null);
  }

  public static UiAction help() {
    return new UiAction(Type.HELP, null);
  }

  public static UiAction run(String regionId) {
    return new UiAction(Type.RUN, regionId);
  }

  public static UiAction runNew(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static UiAction use(NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static UiAction stop(NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static UiAction confirmStop(NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Optional<NodeRef> nodeRef() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public String encode() {
    String data = arg == null ? type.name() : type.name() + SEPARATOR + arg;
    if (data.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_BYTES) {
      throw new IllegalArgumentException("Callback data is longer than %d bytes: %s".formatted(MAX_DATA_BYTES, data));
    }
    return data;
  }

  public static Optional<UiAction> decode(String data) {
    if (StringUtils.isBlank(data)) {
      return Optional.empty();
    }
    String typeName = StringUtils.substringBefore(data, SEPARATOR);
    String arg = data.contains(SEPARATOR) ? StringUtils.substringAfter(data, SEPARATOR) : null;
    return Arrays.stream(Type.values())
        .filter(type -> type.name().equals(typeName))
        .filter(type -> isValid(type, arg))
        .findFirst()
        .map(type -> new UiAction(type, arg));
  }

  private static boolean isValid(Type type, String arg) {
    if (type == null) {
      return false;
    }
    return type == Type.RUN ? StringUtils.isNotBlank(arg) : arg == null;
  }

}
