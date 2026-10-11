package org.github.akarkin1.ui.screen;

import org.github.akarkin1.translation.MainMessagesTranslator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.Html;
import org.github.akarkin1.ui.messenger.RenderedMessage;
import org.github.akarkin1.ui.messenger.ScreenRenderer;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared fixtures and assertions for screen tests. Expected buttons are written as literal
 * {@link Button} records so the tests do not depend on how {@code UiAction} encodes itself.
 */
final class ScreenTestSupport {

  static final Button MENU = new Button("🏠 ${ui.button.menu}", "HOME", null);
  static final List<Button> LINKS = List.of(
      new Button("📖 ${ui.button.exit-node-guide}", null, Links.EXIT_NODE_GUIDE),
      new Button("⬇️ ${ui.button.get-tailscale}", null, Links.DOWNLOAD));
  static final List<Button> REFRESH_HELP = List.of(
      new Button("🔄 ${ui.button.refresh}", "HOME", null),
      new Button("❓ ${ui.button.help}", "HELP", null));

  private ScreenTestSupport() {
  }

  static RegionLabels regionLabels() {
    return new RegionLabels(
        Map.of("eu-central-1", "Frankfurt",
               "eu-west-2", "London",
               "us-east-1", "N. Virginia",
               "ap-northeast-1", "Tokyo",
               "eu-north-1", "Stockholm",
               "sa-east-1", "Sao Paulo",
               "ap-south-1", "mumbai"),
        Map.of("eu-central-1", "DE",
               "eu-west-2", "GB",
               "us-east-1", "US",
               "ap-northeast-1", "JP",
               "eu-north-1", "SE",
               "sa-east-1", "BR",
               "ap-south-1", "IN"));
  }

  /**
   * AC-7: the number of {@code %s} in the template equals the number of params, none is null, and
   * the template with its {@code ${…}} keys removed formats with the params.
   */
  static void assertPlaceholdersMatchParams(Screen screen) {
    int placeholders = countPlaceholders(screen.template());
    assertEquals(placeholders, screen.params().size(),
                 "placeholders vs params in template: " + screen.template());
    screen.params().forEach(param -> assertNotNull(param, "null param in " + screen.params()));
    String withoutKeys = screen.template().replaceAll("\\$\\{[^}]+}", "");
    assertDoesNotThrow(() -> withoutKeys.formatted(screen.params().toArray()),
                       "template does not format with its params: " + screen.template());
  }

  /** All buttons of the keyboard, row by row. */
  static List<Button> buttons(Screen screen) {
    return screen.keyboard().stream().flatMap(List::stream).toList();
  }

  /**
   * The template with its {@code ${…}} keys left in place, formatted with the HTML-escaped
   * params: what the user sees, up to translation, however the screen splits text and params.
   */
  static String withParams(Screen screen) {
    Object[] escaped = screen.params().stream()
        .map(param -> Html.escape(String.valueOf(param)))
        .toArray();
    return screen.template().formatted(escaped);
  }

  /** The message as sent: rendered by the real renderer over the real message files. */
  static RenderedMessage render(Screen screen, String languageCode) {
    UiContext context = new UiContext(1L, 1L, "viewer", "Viewer", languageCode);
    return new ScreenRenderer(new MainMessagesTranslator()).render(context, screen);
  }

  /** The rendered button labels, row by row. */
  static List<String> renderedLabels(Screen screen, String languageCode) {
    InlineKeyboardMarkup keyboard = render(screen, languageCode).keyboard();
    if (keyboard == null) {
      return List.of();
    }
    return keyboard.getKeyboard().stream()
        .flatMap(List::stream)
        .map(InlineKeyboardButton::getText)
        .toList();
  }

  private static int countPlaceholders(String template) {
    int count = 0;
    int index = template.indexOf("%s");
    while (index >= 0) {
      count++;
      index = template.indexOf("%s", index + 2);
    }
    return count;
  }

}
