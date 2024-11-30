package org.github.akarkin1.tg;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.translation.Translator;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Log4j2
@RequiredArgsConstructor
public class BotCommunicator {

  private final AbsSender sender;
  private final Translator translator;

  @SneakyThrows(TelegramApiException.class)
  public void sendMessageToTheBot(String message, Object ...params) {
    Long chatId = TgRequestContext.getChatId();
    if (chatId == null) {
      log.error("Unable to send response back to user – chatId is null");
      return;
    }


    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(chatId);
    String userLangCode = TgRequestContext.getLanguageCode();
    sendMessage.setText(translator.translate(userLangCode, message, params));
    Message responseMessage = sender.execute(sendMessage);
    log.debug("Received response: {}", responseMessage);
  }
}
