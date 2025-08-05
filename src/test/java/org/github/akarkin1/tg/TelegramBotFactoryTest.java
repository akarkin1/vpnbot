package org.github.akarkin1.tg;

import org.github.akarkin1.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.bots.AbsSender;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TelegramBotFactoryTest {

    @Test
    void testSender_shouldReturnAbsSender() {
        // Given
        String token = "test-token";
        String username = "test-bot";
        ConfigManager configManager = mock(ConfigManager.class);

        // When
        AbsSender sender = TelegramBotFactory.sender(token, username, configManager);

        // Then
        assertNotNull(sender);
    }

}
