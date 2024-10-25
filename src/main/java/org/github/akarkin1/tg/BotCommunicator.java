package org.github.akarkin1.tg;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Log4j2
@RequiredArgsConstructor
public class BotCommunicator {

  private final AbsSender sender;

  @SneakyThrows(TelegramApiException.class)
  public void sendMessageToTheBot(String message) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(TgUserContext.getChatId());
    sendMessage.setText(message);
    Message responseMessage = sender.execute(sendMessage);
    log.debug("Received response: {}", responseMessage);
  }
}
