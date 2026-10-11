package org.github.akarkin1.dispatcher;

import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;

import java.util.List;

/**
 * Shared fixtures for the system-command tests (cleanup-2026-10 §4.1). The Menu button is written
 * as a literal {@link Button} record (as in the screen tests), so the tests depend neither on
 * {@code CommonButtons} being visible outside {@code ui.screen} nor on how {@code UiAction}
 * encodes itself; the callback data {@code HOME} is part of the frozen wire format (§4.4).
 */
public final class SystemCommandTestSupport {

  /** D-4: every system-command reply has exactly this keyboard, one row with the Menu button. */
  public static final List<List<Button>> MENU_ROW =
      List.of(List.of(new Button("🏠 ${ui.button.menu}", "HOME", null)));

  private SystemCommandTestSupport() {
  }

  /**
   * The template with its {@code ${…}} keys left in place, formatted with the params: what the
   * user sees up to translation, however the screen splits text and params.
   */
  public static String text(Screen screen) {
    return screen.template().formatted(screen.params().toArray())
        .replace(System.lineSeparator(), "\n");
  }

}
