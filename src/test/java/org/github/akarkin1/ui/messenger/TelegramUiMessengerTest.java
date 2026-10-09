package org.github.akarkin1.ui.messenger;

import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUiMessengerTest {

  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "ru");
  private static final Integer MESSAGE_ID = 42;
  private static final Integer SENT_MESSAGE_ID = 77;
  private static final Screen SCREEN = new Screen("screen", List.of(), List.of());
  private static final Screen NO_KEYBOARD = new Screen("plain", List.of(), List.of());
  private static final InlineKeyboardMarkup KEYBOARD = keyboard();
  private static final RenderedMessage RENDERED = new RenderedMessage("<b>rendered</b>", KEYBOARD);
  private static final RenderedMessage RENDERED_NO_KEYBOARD = new RenderedMessage("plain text", null);

  @Mock
  private AbsSender sender;
  @Mock
  private ScreenRenderer renderer;

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private TelegramUiMessenger messenger;

  @BeforeEach
  void setUp() {
    messenger = new TelegramUiMessenger(sender, renderer, metrics);
    lenient().when(renderer.render(CONTEXT, SCREEN)).thenReturn(RENDERED);
    lenient().when(renderer.render(CONTEXT, NO_KEYBOARD)).thenReturn(RENDERED_NO_KEYBOARD);
  }

  @Test
  @DisplayName("2a AC-6: send returns the id of the sent message")
  void sendReturnsMessageId() throws TelegramApiException {
    when(sender.execute(any(SendMessage.class))).thenReturn(sentMessage());

    assertEquals(SENT_MESSAGE_ID, messenger.send(CONTEXT, SCREEN));
  }

  @Test
  @DisplayName("2a AC-6: send uses the rendered text and keyboard, HTML parse mode, the chat id and no link previews")
  void sendBasics() throws TelegramApiException {
    when(sender.execute(any(SendMessage.class))).thenReturn(sentMessage());

    messenger.send(CONTEXT, SCREEN);

    SendMessage message = captureSendMessage();
    assertEquals("100", message.getChatId());
    assertEquals("<b>rendered</b>", message.getText());
    assertSame(KEYBOARD, message.getReplyMarkup());
    assertEquals(ParseMode.HTML, message.getParseMode());
    assertEquals(Boolean.TRUE, message.getDisableWebPagePreview());
  }

  @Test
  @DisplayName("2a AC-6: send has no reply markup when the rendered keyboard is null")
  void sendWithoutKeyboard() throws TelegramApiException {
    when(sender.execute(any(SendMessage.class))).thenReturn(sentMessage());

    messenger.send(CONTEXT, NO_KEYBOARD);

    assertNull(captureSendMessage().getReplyMarkup());
  }

  @Test
  @DisplayName("2a AC-6: send executes the Telegram call inside a TELEGRAM timing")
  void sendIsTimed() throws TelegramApiException {
    AtomicBoolean timed = new AtomicBoolean();
    when(sender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
      timed.set(metrics.isTiming(MetricComponent.TELEGRAM));
      return sentMessage();
    });

    messenger.send(CONTEXT, SCREEN);

    assertTrue(timed.get(), "execute was not called inside metrics.time(TELEGRAM, ...)");
  }

  @Test
  @DisplayName("2a AC-6: edit sets message id, HTML parse mode, the rendered text and keyboard")
  void edit() throws TelegramApiException {
    messenger.edit(CONTEXT, MESSAGE_ID, SCREEN);

    EditMessageText edit = captureEdit();
    assertEquals("100", edit.getChatId());
    assertEquals(MESSAGE_ID, edit.getMessageId());
    assertEquals(ParseMode.HTML, edit.getParseMode());
    assertEquals(Boolean.TRUE, edit.getDisableWebPagePreview());
    assertEquals("<b>rendered</b>", edit.getText());
    assertSame(KEYBOARD, edit.getReplyMarkup());
  }

  @Test
  @DisplayName("2a AC-6: edit has no reply markup when the rendered keyboard is null")
  void editWithoutKeyboard() throws TelegramApiException {
    messenger.edit(CONTEXT, MESSAGE_ID, NO_KEYBOARD);

    assertNull(captureEdit().getReplyMarkup());
  }

  @Test
  @DisplayName("2a AC-6: edit executes the Telegram call inside a TELEGRAM timing")
  void editIsTimed() throws TelegramApiException {
    AtomicBoolean timed = new AtomicBoolean();
    when(sender.execute(any(EditMessageText.class))).thenAnswer(invocation -> {
      timed.set(metrics.isTiming(MetricComponent.TELEGRAM));
      return true;
    });

    messenger.edit(CONTEXT, MESSAGE_ID, SCREEN);

    assertTrue(timed.get(), "execute was not called inside metrics.time(TELEGRAM, ...)");
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
  @DisplayName("2a AC-6: answerCallback executes the Telegram call inside a TELEGRAM timing")
  void answerCallbackIsTimed() throws TelegramApiException {
    AtomicBoolean timed = new AtomicBoolean();
    when(sender.execute(any(AnswerCallbackQuery.class))).thenAnswer(invocation -> {
      timed.set(metrics.isTiming(MetricComponent.TELEGRAM));
      return true;
    });

    messenger.answerCallback("cb-1");

    assertTrue(timed.get(), "execute was not called inside metrics.time(TELEGRAM, ...)");
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

  private static Message sentMessage() {
    Message message = new Message();
    message.setMessageId(SENT_MESSAGE_ID);
    return message;
  }

  private static InlineKeyboardMarkup keyboard() {
    InlineKeyboardButton button = new InlineKeyboardButton();
    button.setText("Menu");
    button.setCallbackData("HOME");
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    markup.setKeyboard(List.of(List.of(button)));
    return markup;
  }

  @SuppressWarnings("unchecked")
  private static TelegramApiRequestException apiError(String description) {
    ApiResponse<Boolean> response = mock(ApiResponse.class);
    when(response.getErrorDescription()).thenReturn(description);
    return new TelegramApiRequestException("Error editing message text", response);
  }

}
