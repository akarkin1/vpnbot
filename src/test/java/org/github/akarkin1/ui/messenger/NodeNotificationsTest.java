package org.github.akarkin1.ui.messenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.akarkin1.translation.MainMessagesTranslator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.Links;
import org.github.akarkin1.ui.screen.RegionLabels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeNotificationsTest {

  private static final String REGION = "eu-central-1";
  private static final Integer MESSAGE_ID = 42;
  private static final UiContext RU_CONTEXT = new UiContext(100L, "alex", "Alex", "ru");
  private static final UiContext EN_CONTEXT = new UiContext(100L, "bob", "Bob", "en-US");
  private static final String HOST = NodeNotifications.HOSTNAME_PLACEHOLDER;
  private static final String IP = NodeNotifications.PUBLIC_IP_PLACEHOLDER;
  private static final String LABEL = "🇩🇪 Frankfurt";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MainMessagesTranslator messages = new MainMessagesTranslator();
  private final NodeNotifications notifications = new NodeNotifications(
      new LaunchScreens(new RegionLabels(Map.of(REGION, "Frankfurt"), Map.of(REGION, "DE"))),
      new ScreenRenderer(messages));

  @Test
  @DisplayName("2a AC-5, 2b AC-11: placeholders are the documented constants")
  void placeholders() {
    assertEquals("{{HOSTNAME}}", NodeNotifications.HOSTNAME_PLACEHOLDER);
    assertEquals("{{PUBLIC_IP}}", NodeNotifications.PUBLIC_IP_PLACEHOLDER);
    assertEquals("{{TASK_ID}}", NodeNotifications.TASK_ID_PLACEHOLDER);
  }

  @Test
  @DisplayName("2a AC-5: build returns exactly the env keys of §4.4; *_MARKUP only for screens with buttons")
  void exactKeys() {
    Map<String, String> env = notifications.build(RU_CONTEXT, MESSAGE_ID, REGION);

    assertEquals(Set.of("TG_CHAT_ID", "TG_MESSAGE_ID", "TG_READY_TEXT", "TG_READY_MARKUP",
                        "TG_IDLE_WARNING_TEXT", "TG_STOPPED_TEXT", "TG_STOPPED_MARKUP",
                        "TG_STOPPED_CARD_TEXT"),
                 env.keySet());
  }

  @Test
  @DisplayName("2a AC-5: chat id and message id are passed as strings")
  void ids() {
    Map<String, String> env = notifications.build(RU_CONTEXT, MESSAGE_ID, REGION);

    assertEquals("100", env.get("TG_CHAT_ID"));
    assertEquals("42", env.get("TG_MESSAGE_ID"));
  }

  @Test
  @DisplayName("2a AC-5: texts are rendered in the context language (ru) with the placeholders as host and IP")
  void russianTexts() {
    assertTexts(RU_CONTEXT, "ru");
  }

  @Test
  @DisplayName("2a AC-5: texts are rendered in the context language (en) with the placeholders as host and IP")
  void englishTexts() {
    assertTexts(EN_CONTEXT, "en-US");
  }

  @Test
  @DisplayName("2a AC-5, 2b AC-11: ready markup JSON has the links row, then stop (STOP:<region>:{{TASK_ID}}) and menu")
  void readyMarkup() throws Exception {
    Map<String, String> env = notifications.build(RU_CONTEXT, MESSAGE_ID, REGION);

    JsonNode rows = inlineKeyboard(env.get("TG_READY_MARKUP"));
    assertEquals(2, rows.size());
    assertEquals(2, rows.get(0).size());
    assertUrlButton(rows.get(0).get(0), "📖 " + ru("ui.button.exit-node-guide"), Links.EXIT_NODE_GUIDE);
    assertUrlButton(rows.get(0).get(1), "⬇️ " + ru("ui.button.get-tailscale"), Links.DOWNLOAD);
    assertEquals(2, rows.get(1).size());
    assertCallbackButton(rows.get(1).get(0), "🛑 " + ru("ui.button.stop"),
                         "STOP:" + REGION + ":{{TASK_ID}}");
    assertCallbackButton(rows.get(1).get(1), "🏠 " + ru("ui.button.menu"), "HOME");
  }

  @Test
  @DisplayName("2b AC-11: the raw ready markup contains STOP:<region>:{{TASK_ID}} for the node agent to fill in")
  void readyMarkupContainsTaskIdPlaceholder() {
    for (UiContext context : new UiContext[]{RU_CONTEXT, EN_CONTEXT}) {
      String markup = notifications.build(context, MESSAGE_ID, REGION).get("TG_READY_MARKUP");

      assertTrue(markup.contains("STOP:eu-central-1:{{TASK_ID}}"), markup);
    }
  }

  @Test
  @DisplayName("2a AC-5: stopped markup JSON has inline_keyboard with start-again (RUN:<id>) and menu")
  void stoppedMarkup() throws Exception {
    Map<String, String> env = notifications.build(RU_CONTEXT, MESSAGE_ID, REGION);

    JsonNode rows = inlineKeyboard(env.get("TG_STOPPED_MARKUP"));
    assertEquals(1, rows.size());
    assertEquals(2, rows.get(0).size());
    assertCallbackButton(rows.get(0).get(0), "🚀 " + ru("ui.button.start-again"), "RUN:" + REGION);
    assertCallbackButton(rows.get(0).get(1), "🏠 " + ru("ui.button.menu"), "HOME");
  }

  @Test
  @DisplayName("2a AC-5, 2b AC-11: total size of all keys and values stays below 8192 characters")
  void totalSize() {
    for (UiContext context : new UiContext[]{RU_CONTEXT, EN_CONTEXT}) {
      Map<String, String> env = notifications.build(context, MESSAGE_ID, REGION);

      int size = env.entrySet().stream()
          .mapToInt(entry -> entry.getKey().length() + entry.getValue().length())
          .sum();
      assertTrue(size < 8192, "env size " + size + " for " + context.languageCode());
    }
  }

  private void assertTexts(UiContext context, String lang) {
    Map<String, String> env = notifications.build(context, MESSAGE_ID, REGION);

    assertEquals("🟢 <b>" + HOST + "</b> · " + LABEL + "\n🌐 <code>" + IP + "</code>\n⏱ "
                 + value(lang, "ui.node.auto-stop") + "\n\n" + value(lang, "ui.node.connect-hint"),
                 env.get("TG_READY_TEXT"));
    assertEquals("⚠️ <b>" + HOST + "</b> " + value(lang, "ui.node.idle-warning"),
                 env.get("TG_IDLE_WARNING_TEXT"));
    assertEquals("🛑 <b>" + HOST + "</b> " + value(lang, "ui.node.stopped-idle"),
                 env.get("TG_STOPPED_TEXT"));
    assertEquals("⚪ <b>" + HOST + "</b> · " + LABEL + "\n🛑 " + value(lang, "ui.node.stopped"),
                 env.get("TG_STOPPED_CARD_TEXT"));
  }

  private JsonNode inlineKeyboard(String markupJson) throws Exception {
    JsonNode rows = mapper.readTree(markupJson).get("inline_keyboard");
    assertTrue(rows != null && rows.isArray(), "no inline_keyboard array in " + markupJson);
    return rows;
  }

  private static void assertUrlButton(JsonNode button, String text, String url) {
    assertEquals(text, button.path("text").asText());
    assertEquals(url, button.path("url").asText());
  }

  private static void assertCallbackButton(JsonNode button, String text, String callbackData) {
    assertEquals(text, button.path("text").asText());
    assertEquals(callbackData, button.path("callback_data").asText());
  }

  private String ru(String key) {
    return value("ru", key);
  }

  private String value(String lang, String key) {
    return messages.value(lang, key);
  }

}
