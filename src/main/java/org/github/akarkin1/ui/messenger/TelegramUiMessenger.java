package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class TelegramUiMessenger implements UiMessenger {

  private static final String MESSAGE_NOT_MODIFIED = "message is not modified";

  private final AbsSender sender;
  private final Translator translator;

  @Override
  @SneakyThrows(TelegramApiException.class)
  public void send(UiContext context, Screen screen) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(context.chatId());
    sendMessage.setText(text(context, screen));
    sendMessage.setParseMode(ParseMode.HTML);
    sendMessage.setDisableWebPagePreview(true);
    sendMessage.setReplyMarkup(keyboard(context, screen));
    sender.execute(sendMessage);
  }

  @Override
  @SneakyThrows(TelegramApiException.class)
  public void edit(UiContext context, Integer messageId, Screen screen) {
    EditMessageText editMessage = new EditMessageText();
    editMessage.setChatId(context.chatId());
    editMessage.setMessageId(messageId);
    editMessage.setText(text(context, screen));
    editMessage.setParseMode(ParseMode.HTML);
    editMessage.setDisableWebPagePreview(true);
    editMessage.setReplyMarkup(keyboard(context, screen));
    try {
      sender.execute(editMessage);
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
      sender.execute(new AnswerCallbackQuery(callbackQueryId));
    } catch (TelegramApiException e) {
      log.warn("Failed to answer callback query {}", callbackQueryId, e);
    }
  }

  private String text(UiContext context, Screen screen) {
    Object[] escapedParams = screen.params().stream()
        .map(param -> Html.escape(String.valueOf(param)))
        .toArray();
    return translator.translate(context.languageCode(), screen.template(), escapedParams);
  }

  private InlineKeyboardMarkup keyboard(UiContext context, Screen screen) {
    if (screen.keyboard().isEmpty()) {
      return null;
    }

    List<List<InlineKeyboardButton>> rows = screen.keyboard().stream()
        .map(row -> row.stream()
            .map(button -> inlineButton(context, button))
            .toList())
        .toList();
    return new InlineKeyboardMarkup(rows);
  }

  private InlineKeyboardButton inlineButton(UiContext context, Button button) {
    InlineKeyboardButton inlineButton = new InlineKeyboardButton(
        translator.translate(context.languageCode(), button.label()));
    inlineButton.setCallbackData(button.callbackData());
    inlineButton.setUrl(button.url());
    return inlineButton;
  }

}
