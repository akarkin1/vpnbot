package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.MENU_ROW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cleanup-2026-10 §4.1, §4.7 rule 1: /version answers the version given to its constructor. */
class VersionCommandTest {

  private static final UiContext CONTEXT = new UiContext(100L, 7L, "root", "Root", null, "en");

  @Test
  @DisplayName("§4.1: the reply is ${command.version.app-version.message} with the injected version as a param, with the Menu row")
  void injectedVersion() {
    Screen screen = new VersionCommand("2026.10-SNAPSHOT").run(CONTEXT, List.of());

    assertTrue(screen.template().contains("${command.version.app-version.message}"), screen.template());
    assertEquals(List.of("2026.10-SNAPSHOT"), screen.params());
    assertEquals(MENU_ROW, screen.keyboard());
  }

  @Test
  @DisplayName("§4.7 rule 1: nothing static – two instances answer their own version")
  void versionPerInstance() {
    assertEquals(List.of("1.0"), new VersionCommand("1.0").run(CONTEXT, List.of()).params());
    assertEquals(List.of("2.0"), new VersionCommand("2.0").run(CONTEXT, List.of()).params());
  }

  @Test
  @DisplayName("§4.1: /version requires ROOT_ACCESS and has its description key")
  void permissionAndDescription() {
    VersionCommand command = new VersionCommand("2026.10");

    assertEquals(List.of(Permission.ROOT_ACCESS), command.getRequiredPermissions());
    assertEquals("${command.version.description.message}", command.getDescription());
  }

}
