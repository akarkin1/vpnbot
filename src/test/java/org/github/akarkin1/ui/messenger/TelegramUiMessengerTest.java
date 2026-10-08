package org.github.akarkin1.ui.messenger;

import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUiMessengerTest {

  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "ru");
  private static final Integer MESSAGE_ID = 42;
  private static final Screen SCREEN = new Screen(
      "Hi %s, <b>%s</b> %s",
      List.of("<x>", "a&\"b", 5),
      List.of(List.of(new Button("A", "HOME", null), new Button("B", null, "https://example.com")),
              List.of(new Button("C", "RUN:eu-central-1", null))));
  private static final Screen NO_KEYBOARD = new Screen("plain", List.of(), List.of());

  @Mock
  private AbsSender sender;

  /** Every translator call as [langCode, message, params...]. */
  private final List<List<Object>> translations = new ArrayList<>();
  private Translator translator;
  private TelegramUiMessenger messenger;

  @BeforeEach
  void setUp() {
    translator = mock(Translator.class, invocation -> {
      if (!invocation.getMethod().getName().equals("translate")) {
        return RETURNS_DEFAULTS.answer(invocation);
      }
      Object[] raw = invocation.getRawArguments();
      String langCode = (String) raw[0];
      String message = (String) raw[1];
      Object[] params = raw.length > 2 && raw[2] != null ? (Object[]) raw[2] : new Object[0];
      List<Object> call = new ArrayList<>(List.of(langCode, message));
      call.addAll(Arrays.asList(params));
      translations.add(call);
      return "[" + langCode + "]" + message.formatted(params);
    });
    messenger = new TelegramUiMessenger(sender, translator);
  }

  @Test
  @DisplayName("AC-13: send uses HTML parse mode, the chat id and disables link previews")
  void sendBasics() throws TelegramApiException {
    messenger.send(CONTEXT, SCREEN);

    SendMessage message = captureSendMessage();
    assertEquals("100", message.getChatId());
    assertEquals("HTML", message.getParseMode());
    assertEquals(Boolean.TRUE, message.getDisableWebPagePreview());
  }

  @Test
  @DisplayName("AC-13: params are HTML-escaped before they are passed to the translator")
  void escapedParamsPassedToTranslator() {
    messenger.send(CONTEXT, SCREEN);

    assertTrue(translations.contains(List.of("ru", "Hi %s, <b>%s</b> %s", "&lt;x&gt;", "a&amp;&quot;b", "5")),
               translations.toString());
  }

  @Test
  @DisplayName("AC-13: message text is the translated template with escaped params")
  void translatedText() throws TelegramApiException {
    messenger.send(CONTEXT, SCREEN);

    assertEquals("[ru]Hi &lt;x&gt;, <b>a&amp;&quot;b</b> 5", captureSendMessage().getText());
  }

  @Test
  @DisplayName("AC-13: button labels are translated, callback and url buttons are kept apart")
  void inlineKeyboard() throws TelegramApiException {
    messenger.send(CONTEXT, SCREEN);

    InlineKeyboardMarkup markup =
        assertInstanceOf(InlineKeyboardMarkup.class, captureSendMessage().getReplyMarkup());
    List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
    assertEquals(2, rows.size());
    assertEquals(2, rows.get(0).size());
    assertEquals(1, rows.get(1).size());
    assertButton(rows.get(0).get(0), "[ru]A", "HOME", null);
    assertButton(rows.get(0).get(1), "[ru]B", null, "https://example.com");
    assertButton(rows.get(1).get(0), "[ru]C", "RUN:eu-central-1", null);
  }

  @Test
  @DisplayName("AC-13: send has no reply markup for an empty keyboard")
  void sendWithoutKeyboard() throws TelegramApiException {
    messenger.send(CONTEXT, NO_KEYBOARD);

    assertNull(captureSendMessage().getReplyMarkup());
  }

  @Test
  @DisplayName("AC-13: edit sets message id, HTML parse mode, text and keyboard")
  void edit() throws TelegramApiException {
    messenger.edit(CONTEXT, MESSAGE_ID, SCREEN);

    EditMessageText edit = captureEdit();
    assertEquals("100", edit.getChatId());
    assertEquals(MESSAGE_ID, edit.getMessageId());
    assertEquals("HTML", edit.getParseMode());
    assertEquals(Boolean.TRUE, edit.getDisableWebPagePreview());
    assertEquals("[ru]Hi &lt;x&gt;, <b>a&amp;&quot;b</b> 5", edit.getText());
    assertButton(edit.getReplyMarkup().getKeyboard().get(0).get(1), "[ru]B", null, "https://example.com");
  }

  @Test
  @DisplayName("AC-13: edit has no reply markup for an empty keyboard")
  void editWithoutKeyboard() throws TelegramApiException {
    messenger.edit(CONTEXT, MESSAGE_ID, NO_KEYBOARD);

    assertNull(captureEdit().getReplyMarkup());
  }

  @Test
  @DisplayName("AC-13: \"message is not modified\" is ignored on edit")
  void notModifiedIgnored() throws TelegramApiException {
    TelegramApiRequestException notModified = apiError(
        "Bad Request: message is not modified: specified new message content and reply markup are "
        + "exactly the same as a current content and reply markup of the message");
    when(sender.execute(any(EditMessageText.class))).thenThrow(notModified);

    assertDoesNotThrow(() -> messenger.edit(CONTEXT, MESSAGE_ID, SCREEN));
  }

  @Test
  @DisplayName("AC-13: other Telegram errors on edit propagate")
  void otherEditErrorsPropagate() throws TelegramApiException {
    TelegramApiRequestException error = apiError("Bad Request: message to edit not found");
    when(sender.execute(any(EditMessageText.class))).thenThrow(error);

    TelegramApiException thrown =
        assertThrows(TelegramApiException.class, () -> messenger.edit(CONTEXT, MESSAGE_ID, SCREEN));
    assertSame(error, thrown);
  }

  @Test
  @DisplayName("AC-13: answerCallback answers the callback query by id")
  void answerCallback() throws TelegramApiException {
    messenger.answerCallback("cb-1");

    ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
    verify(sender).execute(captor.capture());
    assertEquals("cb-1", captor.getValue().getCallbackQueryId());
  }

  @Test
  @DisplayName("AC-13: answerCallback failures are swallowed")
  void answerCallbackFailureSwallowed() throws TelegramApiException {
    when(sender.execute(any(AnswerCallbackQuery.class)))
        .thenThrow(new TelegramApiException("query is too old"));

    assertDoesNotThrow(() -> messenger.answerCallback("cb-1"));
  }

  private SendMessage captureSendMessage() throws TelegramApiException {
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(sender).execute(captor.capture());
    return captor.getValue();
  }

  private EditMessageText captureEdit() throws TelegramApiException {
    ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
    verify(sender).execute(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private static TelegramApiRequestException apiError(String description) {
    ApiResponse<Boolean> response = mock(ApiResponse.class);
    when(response.getErrorDescription()).thenReturn(description);
    return new TelegramApiRequestException("Error editing message text", response);
  }

  private static void assertButton(InlineKeyboardButton button, String text, String callbackData,
                                   String url) {
    assertEquals(text, button.getText());
    assertEquals(callbackData, button.getCallbackData());
    assertEquals(url, button.getUrl());
  }

}
