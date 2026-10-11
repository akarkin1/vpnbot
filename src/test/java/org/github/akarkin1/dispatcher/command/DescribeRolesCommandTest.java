package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.MENU_ROW;
import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cleanup-2026-10 §4.1, §4.5: /describeRoles has no dependency and prints the role table of
 * {@code RoleSet.describe()} in today's text ({@code ROLE: [PERMISSION, …]} per role).
 */
class DescribeRolesCommandTest {

  private static final UiContext CONTEXT = new UiContext(100L, 7L, "admin", "Admin", null, "en");

  @Test
  @DisplayName("§4.1, §4.5: the reply is ${command.describe-roles.available-roles.message} and one line per role with its permissions, with the Menu row")
  void describesThreeRoles() {
    Screen screen = new DescribeRolesCommand().run(CONTEXT, List.of());

    String text = text(screen);
    List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
    assertTrue(lines.get(0).contains("${command.describe-roles.available-roles.message}"), text);
    assertEquals(4, lines.size(), text);
    assertTrue(text.contains("NODE_ADMIN: [SUPPORTED_REGIONS, LIST_NODES, RUN_NODES]"), text);
    assertTrue(text.contains("READ_ONLY: [SUPPORTED_REGIONS, LIST_NODES]"), text);
    assertTrue(text.contains("USER_ADMIN: [USER_MANAGEMENT]"), text);
    assertEquals(MENU_ROW, screen.keyboard());
  }

  @Test
  @DisplayName("§4.1: /describeRoles requires USER_MANAGEMENT and has its description key")
  void permissionAndDescription() {
    DescribeRolesCommand command = new DescribeRolesCommand();

    assertEquals(List.of(Permission.USER_MANAGEMENT), command.getRequiredPermissions());
    assertEquals("${command.describe-roles.description.message}", command.getDescription());
  }

}
