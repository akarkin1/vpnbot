package org.github.akarkin1.tg;

import org.github.akarkin1.config.ConfigManager;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.function.Function;

public class TelegramBotFactory {

  public static AbsSender sender(String token, String username) {
    String baseUrl = null;
    if (ConfigManager.isTestEnvironment()) {
      baseUrl = "http://localhost:8081";
    }

    return webhookBot(token, username, x -> null, baseUrl);
  }

  private static TelegramWebhookBot webhookBot(
    String token, String username, Function<Update, BotApiMethod> onUpdate, String baseUrl) {
    return webhookBot(token, username, username, onUpdate, baseUrl);
  }

  private static TelegramWebhookBot webhookBot(
      String token, String username, String path, Function<Update, BotApiMethod> onUpdate, String baseUrl) {
    return new TelegramWebhookBot() {
      @Override
      public String getBotToken() {
        return token;
      }

      @Override
      public BotApiMethod onWebhookUpdateReceived(Update update) {
        return onUpdate.apply(update);
      }

      @Override
      public String getBotUsername() {
        return username;
      }

      @Override
      public String getBotPath() {
        return path;
      }

      @Override
      public String getBaseUrl() {
        if (baseUrl != null) {
          return baseUrl;
        }

        return super.getBaseUrl();
      }
    };
  }

}
