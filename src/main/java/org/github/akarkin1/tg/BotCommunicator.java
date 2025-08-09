package org.github.akarkin1.tg;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.translation.Translator;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Log4j2
public class BotCommunicator {

  private final AbsSender sender;
  private final Translator translator;

  @Inject
  public BotCommunicator(AbsSender sender, Translator translator) {
    this.sender = sender;
    this.translator = translator;
  }

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
    log.debug("Translating a message to user's language: {}", userLangCode);
    sendMessage.setText(translator.translate(userLangCode, message, params));
    log.debug("Sending the message to user: {}", sendMessage.getText());
    Message responseMessage = sender.execute(sendMessage);
    log.debug("Received response: {}", responseMessage);
  }
}
