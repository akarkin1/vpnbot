package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.Serializable;

@Log4j2
@RequiredArgsConstructor
public class TelegramUiMessenger implements UiMessenger {

  private static final String MESSAGE_NOT_MODIFIED = "message is not modified";

  private final AbsSender sender;
  private final ScreenRenderer renderer;
  private final RequestMetrics metrics;

  @Override
  @SneakyThrows(TelegramApiException.class)
  public Integer send(UiContext context, Screen screen) {
    RenderedMessage message = renderer.render(context, screen);
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(context.chatId());
    sendMessage.setText(message.text());
    sendMessage.setParseMode(ParseMode.HTML);
    sendMessage.setDisableWebPagePreview(true);
    sendMessage.setReplyMarkup(message.keyboard());
    return execute(sendMessage).getMessageId();
  }

  @Override
  @SneakyThrows(TelegramApiException.class)
  public void edit(UiContext context, Integer messageId, Screen screen) {
    RenderedMessage message = renderer.render(context, screen);
    EditMessageText editMessage = new EditMessageText();
    editMessage.setChatId(context.chatId());
    editMessage.setMessageId(messageId);
    editMessage.setText(message.text());
    editMessage.setParseMode(ParseMode.HTML);
    editMessage.setDisableWebPagePreview(true);
    editMessage.setReplyMarkup(message.keyboard());
    try {
      execute(editMessage);
    } catch (TelegramApiRequestException e) {
      if (!StringUtils.contains(e.getApiResponse(), MESSAGE_NOT_MODIFIED)) {
        throw e;
      }
      log.debug("Message {} in chat {} is not modified, skipping the edit", messageId,
                context.chatId());
    }
  }

  @Override
  public void answerCallback(String callbackQueryId) {
    try {
      execute(new AnswerCallbackQuery(callbackQueryId));
    } catch (TelegramApiException e) {
      log.warn("Failed to answer callback query {}", callbackQueryId, e);
    }
  }

  // Declares the exception that executeUnchecked rethrows, so callers can catch it.
  private <T extends Serializable> T execute(BotApiMethod<T> method) throws TelegramApiException {
    return metrics.time(MetricComponent.TELEGRAM, () -> executeUnchecked(method));
  }

  @SneakyThrows(TelegramApiException.class)
  private <T extends Serializable> T executeUnchecked(BotApiMethod<T> method) {
    return sender.execute(method);
  }

}
