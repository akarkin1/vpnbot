package org.github.akarkin1.dispatcher;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.MENU_ROW;
import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * cleanup-2026-10 §4.1, §7, AC-1, AC-3: the dispatcher routes "/command args" to the registered
 * {@link BotCommand}, checks its permissions against {@code context.user()} and sends every reply
 * as a {@link Screen} through {@link UiMessenger#send}. Everything is constructed here; the
 * {@link Authorizer} mock grants permissions per user from {@link #grant}.
 */
@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

  private static final UiContext ALEX = new UiContext(CHAT_ID, USER_ID, "alex", "Alex", null, "en");
  private static final UiContext BOB = new UiContext(200L, 8L, "bob", "Bob", null, "ru");
  private static final Screen PONG = new Screen("pong %s", List.of("!"), MENU_ROW);
  private static final Screen ADMIN_REPLY = new Screen("admin", List.of(), MENU_ROW);
  private static final List<String> RETIRED = List.of(
      "/runNodeIn eu-central-1", "/listRunningNodes", "/assignRoles alice NODE_ADMIN",
      "/deleteUsers alice", "/listRegisteredUsers");

  @Mock
  private UiMessenger messenger;
  @Mock
  private Authorizer authorizer;

  private CommandDispatcher dispatcher;
  private RecordingCommand ping;
  private RecordingCommand admin;
  private final List<Grant> grants = new ArrayList<>();

  @BeforeEach
  void setUp() {
    lenient().when(authorizer.hasPermission(any(), any())).thenAnswer(invocation -> {
      TgUser user = invocation.getArgument(0);
      Permission permission = invocation.getArgument(1);
      return grants.stream().anyMatch(grant -> grant.matches(user, permission));
    });
    dispatcher = new CommandDispatcher(messenger, authorizer);
    ping = new RecordingCommand("${ping.description}", List.of(Permission.LIST_NODES), PONG);
    admin = new RecordingCommand("${admin.description}", List.of(Permission.ROOT_ACCESS), ADMIN_REPLY);
    dispatcher.registerCommand("/ping", ping);
    dispatcher.registerCommand("/admin", admin);
  }

  @Test
  @DisplayName("AC-1: a registered command runs with the given context and its screen is sent to that context with the Menu row")
  void registeredCommandRuns() {
    grant(ALEX, Permission.LIST_NODES);

    dispatcher.handle(ALEX, "/ping");

    assertEquals(List.of(new Call(ALEX, List.of())), ping.calls);
    Screen sent = sentScreen(ALEX);
    assertSame(PONG, sent, "the command's screen is sent as returned");
    assertEquals(MENU_ROW, sent.keyboard());
  }

  @Test
  @DisplayName("§7: the arguments after the command name are split on whitespace and passed to the command")
  void argumentsSplitOnWhitespace() {
    grant(ALEX, Permission.LIST_NODES);

    dispatcher.handle(ALEX, "/ping eu-central-1  node-1\tthird");

    assertEquals(List.of(new Call(ALEX, List.of("eu-central-1", "node-1", "third"))), ping.calls);
  }

  @Test
  @DisplayName("AC-1: the permission check uses context.user() – a user without a username is checked by the user id")
  void permissionCheckUsesContextUser() {
    UiContext anna = new UiContext(CHAT_ID, USER_ID, null, "Anna", null, "en");
    grant(anna, Permission.LIST_NODES);

    dispatcher.handle(anna, "/ping");

    assertEquals(List.of(new Call(anna, List.of())), ping.calls);
    verify(authorizer).hasPermission(new TgUser(USER_ID, null), Permission.LIST_NODES);
  }

  @Test
  @DisplayName("AC-1: a command whose permission the user lacks is not run; the reply is ${user.not-authorized.error} with the Menu row")
  void notAuthorized() {
    grant(ALEX, Permission.SUPPORTED_REGIONS);

    dispatcher.handle(ALEX, "/ping");

    assertTrue(ping.calls.isEmpty(), "command must not run");
    Screen sent = sentScreen(ALEX);
    assertTrue(sent.template().contains("${user.not-authorized.error}"), sent.template());
    assertEquals(MENU_ROW, sent.keyboard());
  }

  @Test
  @DisplayName("AC-1: a command needs every one of its required permissions")
  void everyRequiredPermissionIsNeeded() {
    RecordingCommand both = new RecordingCommand("${both.description}",
                                                 List.of(Permission.LIST_NODES, Permission.RUN_NODES), PONG);
    dispatcher.registerCommand("/both", both);
    grant(ALEX, Permission.LIST_NODES);

    dispatcher.handle(ALEX, "/both");

    assertTrue(both.calls.isEmpty(), "command must not run");
    assertTrue(sentScreen(ALEX).template().contains("${user.not-authorized.error}"));
  }

  @Test
  @DisplayName("AC-1: an unknown command gets the not-supported text plus the /help list of the commands the caller may use, with the Menu row")
  void unknownCommand() {
    grant(ALEX, Permission.LIST_NODES);

    dispatcher.handle(ALEX, "/unknown arg");

    Screen sent = sentScreen(ALEX);
    assertNotSupported(sent, "/unknown");
    assertTrue(sent.template().contains("/help"), sent.template());
    assertTrue(sent.template().contains("/ping"), sent.template());
    assertFalse(sent.template().contains("/admin"), "not allowed for the caller: " + sent.template());
    assertTrue(ping.calls.isEmpty());
    assertTrue(admin.calls.isEmpty());
  }

  @Test
  @DisplayName("AC-1, §4.1: a retired command (/runNodeIn, /listRunningNodes, /assignRoles, /deleteUsers, /listRegisteredUsers) is answered as unknown")
  void retiredCommands() {
    grant(ALEX, Permission.LIST_NODES);

    RETIRED.forEach(text -> dispatcher.handle(ALEX, text));

    ArgumentCaptor<Screen> screens = ArgumentCaptor.forClass(Screen.class);
    verify(messenger, times(RETIRED.size())).send(eq(ALEX), screens.capture());
    for (int i = 0; i < RETIRED.size(); i++) {
      assertNotSupported(screens.getAllValues().get(i), RETIRED.get(i).split(" ")[0]);
    }
  }

  @Test
  @DisplayName("§7, §4.7 rule 5: /help is registered by the constructor and needs no permission")
  void helpRegisteredByConstructor() {
    CommandDispatcher bare = new CommandDispatcher(messenger, authorizer);

    bare.handle(BOB, "/help");

    Screen sent = sentScreen(BOB);
    assertTrue(sent.template().startsWith("${command.help.app-description.message}"), sent.template());
    assertTrue(sent.template().contains("/help"), sent.template());
    assertTrue(sent.template().contains("${command.help.description.message}"), sent.template());
    assertEquals(MENU_ROW, sent.keyboard());
  }

  @Test
  @DisplayName("§4.1: supportedCommands lists one line per command the user may use, in registration order, /help first")
  void supportedCommands() {
    grant(ALEX, Permission.LIST_NODES);

    List<String> lines = dispatcher.supportedCommands(ALEX.user()).lines().toList();

    assertEquals(2, lines.size(), String.valueOf(lines));
    assertTrue(lines.get(0).contains("/help") && lines.get(0).contains("${command.help.description.message}"),
               lines.get(0));
    assertTrue(lines.get(1).contains("/ping") && lines.get(1).contains("${ping.description}"), lines.get(1));
  }

  @Test
  @DisplayName("§4.1: a user with every permission sees every registered command")
  void supportedCommandsForPowerUser() {
    grant(ALEX, Permission.LIST_NODES, Permission.ROOT_ACCESS);

    String list = dispatcher.supportedCommands(ALEX.user());

    assertEquals(3, list.lines().count(), list);
    assertTrue(list.contains("/admin") && list.contains("${admin.description}"), list);
  }

  @Test
  @DisplayName("AC-1: nothing is read from static state – consecutive requests are checked and answered with their own context")
  void noStaticState() {
    grant(ALEX, Permission.LIST_NODES);

    dispatcher.handle(ALEX, "/ping");
    dispatcher.handle(BOB, "/ping");

    assertEquals(List.of(new Call(ALEX, List.of())), ping.calls);
    assertSame(PONG, sentScreen(ALEX));
    assertTrue(sentScreen(BOB).template().contains("${user.not-authorized.error}"));
  }

  @Test
  @DisplayName("AC-3: an exception from a command is answered with the generic error screen and not rethrown")
  void commandExceptionAnsweredWithErrorScreen() {
    dispatcher.registerCommand("/fail", new RecordingCommand("${fail.description}", List.of(), null) {
      @Override
      public Screen run(UiContext context, List<String> args) {
        throw new IllegalStateException("boom");
      }
    });

    dispatcher.handle(ALEX, "/fail");

    assertEquals(new ErrorScreen().generic(), sentScreen(ALEX));
  }

  private void assertNotSupported(Screen sent, String command) {
    String list = dispatcher.supportedCommands(ALEX.user());
    assertTrue(sent.template().startsWith("${common.command.not-supported.error}"), sent.template());
    assertTrue(sent.template().endsWith(list), "ends with the /help list: " + sent.template());
    assertEquals(List.of(command), sent.params());
    assertEquals(MENU_ROW, sent.keyboard());
  }

  private Screen sentScreen(UiContext context) {
    ArgumentCaptor<Screen> screen = ArgumentCaptor.forClass(Screen.class);
    verify(messenger).send(eq(context), screen.capture());
    return screen.getValue();
  }

  private void grant(UiContext context, Permission... permissions) {
    grants.add(new Grant(context.userId(), context.username(), Set.of(permissions)));
  }

  /** Permissions of one user; matches only the exact TgUser built from the context. */
  private record Grant(Long userId, String username, Set<Permission> permissions) {

    boolean matches(TgUser user, Permission permission) {
      return Objects.equals(user.id(), userId) && Objects.equals(user.username(), username)
          && permissions.contains(permission);
    }

  }

  private record Call(UiContext context, List<String> args) {
  }

  private static class RecordingCommand implements BotCommand {

    private final String description;
    private final List<Permission> permissions;
    private final Screen reply;
    private final List<Call> calls = new ArrayList<>();

    RecordingCommand(String description, List<Permission> permissions, Screen reply) {
      this.description = description;
      this.permissions = permissions;
      this.reply = reply;
    }

    @Override
    public Screen run(UiContext context, List<String> args) {
      calls.add(new Call(context, List.copyOf(args)));
      return reply;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public List<Permission> getRequiredPermissions() {
      return permissions;
    }

  }

}
