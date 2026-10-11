package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.UserRole;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A button action, sent as callback data {@code TAG[:arg]} of at most 64 UTF-8 bytes. One record
 * per action; each validates its components, renders its callback data ({@link #encode()}) and
 * parses its argument ({@code parse}). The wire format and the tags are frozen: buttons in messages
 * sent by earlier versions must keep working.
 *
 * <p>{@code <id>} is a Telegram user id, {@code <mask>} the {@link RoleSet} bitmask of the selected
 * roles and {@code <key>} a {@code TgUser.key()}: the id, or {@code @username} of a pending user.
 */
public sealed interface UiAction {

  /**
   * The parser of each tag. A parser gets the text after the first {@code ':'}, or {@code null}
   * when the data has none, and returns empty for anything invalid.
   */
  Map<String, Function<String, Optional<? extends UiAction>>> PARSERS = Map.ofEntries(
      Map.entry(Home.TAG, Home::parse),
      Map.entry(Help.TAG, Help::parse),
      Map.entry(Run.TAG, Run::parse),
      Map.entry(RunNew.TAG, RunNew::parse),
      Map.entry(Use.TAG, Use::parse),
      Map.entry(Stop.TAG, Stop::parse),
      Map.entry(ConfirmStop.TAG, ConfirmStop::parse),
      Map.entry(RequestAccess.TAG, RequestAccess::parse),
      Map.entry(AccessToggle.TAG, AccessToggle::parse),
      Map.entry(AccessGrant.TAG, AccessGrant::parse),
      Map.entry(AccessDecline.TAG, AccessDecline::parse),
      Map.entry(Users.TAG, Users::parse),
      Map.entry(ShowUser.TAG, ShowUser::parse),
      Map.entry(ToggleRole.TAG, ToggleRole::parse),
      Map.entry(DeleteUser.TAG, DeleteUser::parse),
      Map.entry(ConfirmDeleteUser.TAG, ConfirmDeleteUser::parse));

  /**
   * The callback data.
   *
   * @throws IllegalArgumentException if it is longer than 64 UTF-8 bytes
   */
  String encode();

  /** The action of the callback data; empty for an unknown tag or an invalid argument. */
  static Optional<UiAction> decode(String data) {
    if (data == null) {
      return Optional.empty();
    }
    int separator = data.indexOf(':');
    String tag = separator < 0 ? data : data.substring(0, separator);
    String arg = separator < 0 ? null : data.substring(separator + 1);
    Function<String, Optional<? extends UiAction>> parser = PARSERS.get(tag);
    return parser == null ? Optional.empty() : parser.apply(arg).map(UiAction.class::cast);
  }

  // --- the actions ---------------------------------------------------------------------------

  /** {@code HOME}: refresh the home card. */
  record Home() implements UiAction {
    private static final String TAG = "HOME";

    @Override
    public String encode() {
      return render(TAG);
    }

    public static Optional<Home> parse(String arg) {
      return arg == null ? Optional.of(new Home()) : Optional.empty();
    }
  }

  /** {@code HELP}. */
  record Help() implements UiAction {
    private static final String TAG = "HELP";

    @Override
    public String encode() {
      return render(TAG);
    }

    public static Optional<Help> parse(String arg) {
      return arg == null ? Optional.of(new Help()) : Optional.empty();
    }
  }

  /** {@code RUN:<regionId>}: start a node in the region (or show the running ones). */
  record Run(String regionId) implements UiAction {
    private static final String TAG = "RUN";

    public Run {
      requireRegionId(regionId);
    }

    @Override
    public String encode() {
      return render(TAG, regionId);
    }

    public static Optional<Run> parse(String arg) {
      return StringUtils.isBlank(arg) ? Optional.empty() : Optional.of(new Run(arg));
    }
  }

  /** {@code RUN_NEW:<regionId>}: start another node in the region. */
  record RunNew(String regionId) implements UiAction {
    private static final String TAG = "RUN_NEW";

    public RunNew {
      requireRegionId(regionId);
    }

    @Override
    public String encode() {
      return render(TAG, regionId);
    }

    public static Optional<RunNew> parse(String arg) {
      return StringUtils.isBlank(arg) ? Optional.empty() : Optional.of(new RunNew(arg));
    }
  }

  /** {@code USE:<regionId>:<taskId>}: how to use the node. */
  record Use(NodeRef node) implements UiAction {
    private static final String TAG = "USE";

    public Use {
      requireNodeRef(node);
    }

    @Override
    public String encode() {
      return render(TAG, node.encode());
    }

    public static Optional<Use> parse(String arg) {
      return NodeRef.parse(arg).map(Use::new);
    }
  }

  /** {@code STOP:<regionId>:<taskId>}: ask to confirm stopping the node. */
  record Stop(NodeRef node) implements UiAction {
    private static final String TAG = "STOP";

    public Stop {
      requireNodeRef(node);
    }

    @Override
    public String encode() {
      return render(TAG, node.encode());
    }

    public static Optional<Stop> parse(String arg) {
      return NodeRef.parse(arg).map(Stop::new);
    }
  }

  /** {@code STOP_CONFIRM:<regionId>:<taskId>}: stop the node. */
  record ConfirmStop(NodeRef node) implements UiAction {
    private static final String TAG = "STOP_CONFIRM";

    public ConfirmStop {
      requireNodeRef(node);
    }

    @Override
    public String encode() {
      return render(TAG, node.encode());
    }

    public static Optional<ConfirmStop> parse(String arg) {
      return NodeRef.parse(arg).map(ConfirmStop::new);
    }
  }

  /** {@code REQUEST_ACCESS}: a user without access asks the admins. */
  record RequestAccess() implements UiAction {
    private static final String TAG = "REQUEST_ACCESS";

    @Override
    public String encode() {
      return render(TAG);
    }

    public static Optional<RequestAccess> parse(String arg) {
      return arg == null ? Optional.of(new RequestAccess()) : Optional.empty();
    }
  }

  /** {@code ACCESS_TOGGLE:<id>:<mask>:<ROLE>}: an admin flips a role of an access request. */
  record AccessToggle(long userId, Set<UserRole> selected, UserRole role) implements UiAction {
    private static final String TAG = "ACCESS_TOGGLE";

    public AccessToggle {
      requireUserId(userId);
      selected = copyRoles(selected);
      requireRole(role);
    }

    @Override
    public String encode() {
      return render(TAG, userId, RoleSet.encode(selected), role.name());
    }

    public static Optional<AccessToggle> parse(String arg) {
      String[] parts = split(arg, 3);
      if (parts.length == 0) {
        return Optional.empty();
      }
      Optional<Long> userId = parseUserId(parts[0]);
      Optional<Set<UserRole>> selected = parseMask(parts[1]);
      Optional<UserRole> role = parseRole(parts[2]);
      if (userId.isEmpty() || selected.isEmpty() || role.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new AccessToggle(userId.get(), selected.get(), role.get()));
    }
  }

  /** {@code ACCESS_GRANT:<id>:<mask>}: an admin grants the selected roles. */
  record AccessGrant(long userId, Set<UserRole> selected) implements UiAction {
    private static final String TAG = "ACCESS_GRANT";

    public AccessGrant {
      requireUserId(userId);
      selected = copyRoles(selected);
    }

    @Override
    public String encode() {
      return render(TAG, userId, RoleSet.encode(selected));
    }

    public static Optional<AccessGrant> parse(String arg) {
      String[] parts = split(arg, 2);
      if (parts.length == 0) {
        return Optional.empty();
      }
      Optional<Long> userId = parseUserId(parts[0]);
      Optional<Set<UserRole>> selected = parseMask(parts[1]);
      if (userId.isEmpty() || selected.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new AccessGrant(userId.get(), selected.get()));
    }
  }

  /** {@code ACCESS_DECLINE:<id>}: an admin declines an access request. */
  record AccessDecline(long userId) implements UiAction {
    private static final String TAG = "ACCESS_DECLINE";

    public AccessDecline {
      requireUserId(userId);
    }

    @Override
    public String encode() {
      return render(TAG, userId);
    }

    public static Optional<AccessDecline> parse(String arg) {
      return parseUserId(arg).map(AccessDecline::new);
    }
  }

  /** {@code USERS}: the Users screen. */
  record Users() implements UiAction {
    private static final String TAG = "USERS";

    @Override
    public String encode() {
      return render(TAG);
    }

    public static Optional<Users> parse(String arg) {
      return arg == null ? Optional.of(new Users()) : Optional.empty();
    }
  }

  /** {@code USER:<key>}: one user's card. */
  record ShowUser(String key) implements UiAction {
    private static final String TAG = "USER";

    public ShowUser {
      requireKey(key);
    }

    @Override
    public String encode() {
      return render(TAG, key);
    }

    public static Optional<ShowUser> parse(String arg) {
      return isKey(arg) ? Optional.of(new ShowUser(arg)) : Optional.empty();
    }
  }

  /** {@code USER_ROLE:<key>:<ROLE>}: flip a role of a user. */
  record ToggleRole(String key, UserRole role) implements UiAction {
    private static final String TAG = "USER_ROLE";

    public ToggleRole {
      requireKey(key);
      requireRole(role);
    }

    @Override
    public String encode() {
      return render(TAG, key, role.name());
    }

    public static Optional<ToggleRole> parse(String arg) {
      String[] parts = split(arg, 2);
      if (parts.length == 0 || !isKey(parts[0])) {
        return Optional.empty();
      }
      return parseRole(parts[1]).map(role -> new ToggleRole(parts[0], role));
    }
  }

  /** {@code USER_DEL:<key>}: ask to confirm deleting a user. */
  record DeleteUser(String key) implements UiAction {
    private static final String TAG = "USER_DEL";

    public DeleteUser {
      requireKey(key);
    }

    @Override
    public String encode() {
      return render(TAG, key);
    }

    public static Optional<DeleteUser> parse(String arg) {
      return isKey(arg) ? Optional.of(new DeleteUser(arg)) : Optional.empty();
    }
  }

  /** {@code USER_DEL_CONFIRM:<key>}: delete a user. */
  record ConfirmDeleteUser(String key) implements UiAction {
    private static final String TAG = "USER_DEL_CONFIRM";

    public ConfirmDeleteUser {
      requireKey(key);
    }

    @Override
    public String encode() {
      return render(TAG, key);
    }

    public static Optional<ConfirmDeleteUser> parse(String arg) {
      return isKey(arg) ? Optional.of(new ConfirmDeleteUser(arg)) : Optional.empty();
    }
  }

  // --- factories (the screens build their buttons with these) ---------------------------------

  static Home home() {
    return new Home();
  }

  static Help help() {
    return new Help();
  }

  static Run run(String regionId) {
    return new Run(regionId);
  }

  static RunNew runNew(String regionId) {
    return new RunNew(regionId);
  }

  static Use use(NodeRef node) {
    return new Use(node);
  }

  static Stop stop(NodeRef node) {
    return new Stop(node);
  }

  static ConfirmStop confirmStop(NodeRef node) {
    return new ConfirmStop(node);
  }

  static RequestAccess requestAccess() {
    return new RequestAccess();
  }

  static AccessToggle accessToggle(long userId, Set<UserRole> selected, UserRole role) {
    return new AccessToggle(userId, selected, role);
  }

  static AccessGrant accessGrant(long userId, Set<UserRole> selected) {
    return new AccessGrant(userId, selected);
  }

  static AccessDecline accessDecline(long userId) {
    return new AccessDecline(userId);
  }

  static Users users() {
    return new Users();
  }

  static ShowUser user(String key) {
    return new ShowUser(key);
  }

  static ToggleRole toggleRole(String key, UserRole role) {
    return new ToggleRole(key, role);
  }

  static DeleteUser deleteUser(String key) {
    return new DeleteUser(key);
  }

  static ConfirmDeleteUser confirmDeleteUser(String key) {
    return new ConfirmDeleteUser(key);
  }

  // --- encoding and validation ---------------------------------------------------------------

  /** {@code tag[:part[:part…]]}, at most 64 UTF-8 bytes. */
  private static String render(String tag, Object... parts) {
    StringBuilder data = new StringBuilder(tag);
    for (Object part : parts) {
      data.append(':').append(part);
    }
    String encoded = data.toString();
    if (encoded.getBytes(StandardCharsets.UTF_8).length > 64) {
      throw new IllegalArgumentException("Callback data is longer than 64 bytes: " + encoded);
    }
    return encoded;
  }

  /** The {@code ':'}-separated parts of the argument; an empty array unless there are exactly {@code count}. */
  private static String[] split(String arg, int count) {
    if (arg == null) {
      return new String[0];
    }
    String[] parts = arg.split(":", -1);
    return parts.length == count ? parts : new String[0];
  }

  /** A positive decimal id of 1–19 ASCII digits. */
  private static Optional<Long> parseUserId(String value) {
    if (value == null || value.isEmpty() || value.length() > 19 || !isAsciiDigits(value)) {
      return Optional.empty();
    }
    try {
      long id = Long.parseLong(value);
      return id > 0 ? Optional.of(id) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** A one-digit {@link RoleSet} mask. */
  private static Optional<Set<UserRole>> parseMask(String value) {
    if (value.length() != 1 || !isAsciiDigits(value)) {
      return Optional.empty();
    }
    int mask = value.charAt(0) - '0';
    return RoleSet.isValid(mask) ? Optional.of(RoleSet.decode(mask)) : Optional.empty();
  }

  /** The role with exactly this name. */
  private static Optional<UserRole> parseRole(String value) {
    for (UserRole role : UserRole.values()) {
      if (role.name().equals(value)) {
        return Optional.of(role);
      }
    }
    return Optional.empty();
  }

  /** A {@code TgUser.key()}: a positive id, or {@code @} + a username without {@code ':'} or whitespace. */
  private static boolean isKey(String key) {
    if (key == null) {
      return false;
    }
    if (!key.startsWith("@")) {
      return parseUserId(key).isPresent();
    }
    String username = key.substring(1);
    return !username.isEmpty()
           && username.chars().noneMatch(c -> c == ':' || Character.isWhitespace(c));
  }

  private static boolean isAsciiDigits(String value) {
    return value.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  private static void requireRegionId(String regionId) {
    if (StringUtils.isBlank(regionId)) {
      throw new IllegalArgumentException("Blank region id: " + regionId);
    }
  }

  private static void requireNodeRef(NodeRef node) {
    if (node == null || !NodeRef.parse(node.encode()).equals(Optional.of(node))) {
      throw new IllegalArgumentException("Invalid node reference: " + node);
    }
  }

  private static void requireUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("Invalid user id: " + userId);
    }
  }

  private static void requireKey(String key) {
    if (!isKey(key)) {
      throw new IllegalArgumentException("Invalid user key: " + key);
    }
  }

  private static void requireRole(UserRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Missing role");
    }
  }

  /** An unmodifiable copy of the selected roles; their mask must be {@link RoleSet#isValid}. */
  private static Set<UserRole> copyRoles(Set<UserRole> selected) {
    if (selected == null) {
      throw new IllegalArgumentException("Missing role selection");
    }
    Set<UserRole> copy = EnumSet.noneOf(UserRole.class);
    for (UserRole role : selected) {
      if (role == null) {
        throw new IllegalArgumentException("Null role in the selection: " + selected);
      }
      copy.add(role);
    }
    if (!RoleSet.isValid(RoleSet.encode(copy))) {
      throw new IllegalArgumentException("Invalid role selection: " + selected);
    }
    return Collections.unmodifiableSet(copy);
  }

}
