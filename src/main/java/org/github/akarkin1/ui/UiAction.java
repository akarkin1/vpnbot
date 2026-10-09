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
    return new UiAction(Type.RUN_NEW, regionId);
  }

  public static UiAction use(NodeRef node) {
    return new UiAction(Type.USE, node.encode());
  }

  public static UiAction stop(NodeRef node) {
    return new UiAction(Type.STOP, node.encode());
  }

  public static UiAction confirmStop(NodeRef node) {
    return new UiAction(Type.STOP_CONFIRM, node.encode());
  }

  public Optional<NodeRef> nodeRef() {
    return hasNodeRef(type) ? NodeRef.parse(arg) : Optional.empty();
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
    return switch (type) {
      case RUN, RUN_NEW -> StringUtils.isNotBlank(arg);
      case USE, STOP, STOP_CONFIRM -> NodeRef.parse(arg).isPresent();
      case HOME, HELP -> arg == null;
    };
  }

  private static boolean hasNodeRef(Type type) {
    return type == Type.USE || type == Type.STOP || type == Type.STOP_CONFIRM;
  }

}
