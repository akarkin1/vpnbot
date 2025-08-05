package org.github.akarkin1.tg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class TelegramBotFactoryTest {

    @Test
    void testSender_shouldReturnAbsSender() {
        // Given
        String token = "test-token";
        String username = "test-bot";

        // When
        AbsSender sender = TelegramBotFactory.sender(token, username);

        // Then
        assertNotNull(sender);
    }

}
