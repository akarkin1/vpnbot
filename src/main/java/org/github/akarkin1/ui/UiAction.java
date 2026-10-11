package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.UserRole;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A button action as callback data {@code TYPE[:arg]}. The access and user actions carry:
 * {@code ACCESS_TOGGLE:<id>:<mask>:<ROLE>}, {@code ACCESS_GRANT:<id>:<mask>},
 * {@code ACCESS_DECLINE:<id>}, {@code USER:<key>}, {@code USER_ROLE:<key>:<ROLE>},
 * {@code USER_DEL:<key>}, {@code USER_DEL_CONFIRM:<key>}, where {@code <id>} is a Telegram user id,
 * {@code <mask>} a {@link RoleSet} bitmask and {@code <key>} a {@code TgUser.key()}.
 */
public record UiAction(Type type, String arg) {

  private static final int MAX_DATA_BYTES = 64;
  private static final String SEPARATOR = ":";
  private static final String ID = "(\\d{1,19})";
  private static final String MASK = "(\\d)";
  private static final String ROLE = "([A-Z_]+)";
  /** {@code TgUser.key()}: the id, or {@code @username} of a pending user. */
  private static final String KEY = "(\\d{1,19}|@[^:\\s]+)";
  private static final Pattern ID_ARG = Pattern.compile(ID);
  private static final Pattern ID_MASK_ARG = Pattern.compile(ID + ":" + MASK);
  private static final Pattern ID_MASK_ROLE_ARG = Pattern.compile(ID + ":" + MASK + ":" + ROLE);
  private static final Pattern KEY_ARG = Pattern.compile(KEY);
  private static final Pattern KEY_ROLE_ARG = Pattern.compile(KEY + ":" + ROLE);

  public enum Type {
    HOME, HELP, RUN, RUN_NEW, USE, STOP, STOP_CONFIRM,
    REQUEST_ACCESS, ACCESS_TOGGLE, ACCESS_GRANT, ACCESS_DECLINE,
    USERS, USER, USER_ROLE, USER_DEL, USER_DEL_CONFIRM
  }

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

  public static UiAction requestAccess() {
    return new UiAction(Type.REQUEST_ACCESS, null);
  }

  public static UiAction accessToggle(long userId, int mask, UserRole role) {
    return new UiAction(Type.ACCESS_TOGGLE, userId + SEPARATOR + mask + SEPARATOR + role.name());
  }

  public static UiAction accessGrant(long userId, int mask) {
    return new UiAction(Type.ACCESS_GRANT, userId + SEPARATOR + mask);
  }

  public static UiAction accessDecline(long userId) {
    return new UiAction(Type.ACCESS_DECLINE, String.valueOf(userId));
  }

  public static UiAction users() {
    return new UiAction(Type.USERS, null);
  }

  public static UiAction user(String key) {
    return new UiAction(Type.USER, key);
  }

  public static UiAction toggleRole(String key, UserRole role) {
    return new UiAction(Type.USER_ROLE, key + SEPARATOR + role.name());
  }

  public static UiAction deleteUser(String key) {
    return new UiAction(Type.USER_DEL, key);
  }

  public static UiAction confirmDeleteUser(String key) {
    return new UiAction(Type.USER_DEL_CONFIRM, key);
  }

  public Optional<NodeRef> nodeRef() {
    return hasNodeRef(type) ? NodeRef.parse(arg) : Optional.empty();
  }

  /** The requester's user id of {@code ACCESS_TOGGLE}, {@code ACCESS_GRANT}, {@code ACCESS_DECLINE}. */
  public Optional<Long> userId() {
    return switch (type) {
      case ACCESS_TOGGLE, ACCESS_GRANT, ACCESS_DECLINE -> group(1).map(Long::valueOf);
      default -> Optional.empty();
    };
  }

  /** The {@code TgUser.key()} of {@code USER}, {@code USER_ROLE}, {@code USER_DEL}, {@code USER_DEL_CONFIRM}. */
  public Optional<String> userKey() {
    return switch (type) {
      case USER, USER_ROLE, USER_DEL, USER_DEL_CONFIRM -> group(1);
      default -> Optional.empty();
    };
  }

  /** The selected roles' bitmask of {@code ACCESS_TOGGLE} and {@code ACCESS_GRANT}. */
  public OptionalInt roleMask() {
    return switch (type) {
      case ACCESS_TOGGLE, ACCESS_GRANT -> group(2).map(mask -> OptionalInt.of(Integer.parseInt(mask)))
          .orElse(OptionalInt.empty());
      default -> OptionalInt.empty();
    };
  }

  /** The role of {@code ACCESS_TOGGLE} and {@code USER_ROLE}. */
  public Optional<UserRole> role() {
    return switch (type) {
      case ACCESS_TOGGLE -> group(3).map(UserRole::valueOf);
      case USER_ROLE -> group(2).map(UserRole::valueOf);
      default -> Optional.empty();
    };
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

  private Optional<String> group(int group) {
    Matcher matcher = argPattern(type).matcher(arg);
    return matcher.matches() ? Optional.of(matcher.group(group)) : Optional.empty();
  }

  private static boolean isValid(Type type, String arg) {
    if (type == null) {
      return false;
    }
    return switch (type) {
      case RUN, RUN_NEW -> StringUtils.isNotBlank(arg);
      case USE, STOP, STOP_CONFIRM -> NodeRef.parse(arg).isPresent();
      case HOME, HELP, REQUEST_ACCESS, USERS -> arg == null;
      case ACCESS_TOGGLE, ACCESS_GRANT, ACCESS_DECLINE, USER, USER_ROLE, USER_DEL,
           USER_DEL_CONFIRM -> isValidUserArg(type, arg);
    };
  }

  private static boolean isValidUserArg(Type type, String arg) {
    if (arg == null) {
      return false;
    }
    Matcher matcher = argPattern(type).matcher(arg);
    if (!matcher.matches()) {
      return false;
    }
    return switch (type) {
      case ACCESS_TOGGLE -> isUserId(matcher.group(1))
                            && RoleSet.isValid(Integer.parseInt(matcher.group(2)))
                            && isRole(matcher.group(3));
      case ACCESS_GRANT -> isUserId(matcher.group(1))
                           && RoleSet.isValid(Integer.parseInt(matcher.group(2)));
      case ACCESS_DECLINE -> isUserId(matcher.group(1));
      case USER_ROLE -> isKey(matcher.group(1)) && isRole(matcher.group(2));
      default -> isKey(matcher.group(1));
    };
  }

  private static Pattern argPattern(Type type) {
    return switch (type) {
      case ACCESS_TOGGLE -> ID_MASK_ROLE_ARG;
      case ACCESS_GRANT -> ID_MASK_ARG;
      case ACCESS_DECLINE -> ID_ARG;
      case USER_ROLE -> KEY_ROLE_ARG;
      case USER, USER_DEL, USER_DEL_CONFIRM -> KEY_ARG;
      default -> throw new IllegalStateException("No user argument for " + type);
    };
  }

  private static boolean isUserId(String id) {
    try {
      return Long.parseLong(id) > 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** An id or {@code @username}. */
  private static boolean isKey(String key) {
    return key.startsWith("@") || isUserId(key);
  }

  private static boolean isRole(String role) {
    return Arrays.stream(UserRole.values()).anyMatch(value -> value.name().equals(role));
  }

  private static boolean hasNodeRef(Type type) {
    return type == Type.USE || type == Type.STOP || type == Type.STOP_CONFIRM;
  }

}
