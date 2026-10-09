package org.github.akarkin1.ui.messenger;

import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenRendererTest {

  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "ru");
  private static final Screen SCREEN = new Screen(
      "Hi %s, <b>%s</b> %s",
      List.of("<x>", "a&\"b", 5),
      List.of(List.of(new Button("A", "HOME", null), new Button("B", null, "https://example.com")),
              List.of(new Button("C", "RUN:eu-central-1", null))));
  private static final Screen NO_KEYBOARD = new Screen("plain", List.of(), List.of());

  /** Every translator call as [langCode, message, params...]. */
  private final List<List<Object>> translations = new ArrayList<>();
  private ScreenRenderer renderer;

  @BeforeEach
  void setUp() {
    Translator translator = (langCode, message, params) -> {
      Object[] safeParams = params == null ? new Object[0] : params;
      List<Object> call = new ArrayList<>(List.of(langCode, message));
      call.addAll(Arrays.asList(safeParams));
      translations.add(call);
      return "[" + langCode + "]" + message.formatted(safeParams);
    };
    renderer = new ScreenRenderer(translator);
  }

  @Test
  @DisplayName("2a AC-6: params are HTML-escaped before they are passed to the translator")
  void escapedParamsPassedToTranslator() {
    renderer.render(CONTEXT, SCREEN);

    assertTrue(translations.contains(List.of("ru", "Hi %s, <b>%s</b> %s", "&lt;x&gt;", "a&amp;&quot;b", "5")),
               translations.toString());
  }

  @Test
  @DisplayName("2a AC-6: text is the template translated in the context language with escaped params")
  void translatedText() {
    RenderedMessage message = renderer.render(CONTEXT, SCREEN);

    assertEquals("[ru]Hi &lt;x&gt;, <b>a&amp;&quot;b</b> 5", message.text());
  }

  @Test
  @DisplayName("2a AC-6: button labels are translated, callback and url buttons are kept apart")
  void inlineKeyboard() {
    InlineKeyboardMarkup markup = renderer.render(CONTEXT, SCREEN).keyboard();

    assertNotNull(markup);
    List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
    assertEquals(2, rows.size());
    assertEquals(2, rows.get(0).size());
    assertEquals(1, rows.get(1).size());
    assertButton(rows.get(0).get(0), "[ru]A", "HOME", null);
    assertButton(rows.get(0).get(1), "[ru]B", null, "https://example.com");
    assertButton(rows.get(1).get(0), "[ru]C", "RUN:eu-central-1", null);
  }

  @Test
  @DisplayName("2a AC-6: keyboard is null when the screen has no rows")
  void noKeyboard() {
    RenderedMessage message = renderer.render(CONTEXT, NO_KEYBOARD);

    assertEquals("[ru]plain", message.text());
    assertNull(message.keyboard());
  }

  private static void assertButton(InlineKeyboardButton button, String text, String callbackData,
                                   String url) {
    assertEquals(text, button.getText());
    assertEquals(callbackData, button.getCallbackData());
    assertEquals(url, button.getUrl());
  }

}
