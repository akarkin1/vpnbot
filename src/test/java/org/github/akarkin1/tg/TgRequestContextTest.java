package org.github.akarkin1.tg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TgRequestContextTest {

    @Test
    void testInitContext_shouldSetAllContextFields() {
        // Given
        Update update = createUpdate("testuser", "ru", 123456L);

        // When
        TgRequestContext.initContext(update);

        // Then
        assertEquals("testuser", TgRequestContext.getUsername());
        assertEquals("ru", TgRequestContext.getLanguageCode());
        assertEquals(123456L, TgRequestContext.getChatId());
    }

    @Test
    void testInitContext_whenLanguageCodeIsNull_shouldSetDefaultLanguage() {
        // Given
        Update update = createUpdate("testuser", null, 123456L);

        // When
        TgRequestContext.initContext(update);

        // Then
        assertEquals("testuser", TgRequestContext.getUsername());
        assertEquals("en-US", TgRequestContext.getLanguageCode());
        assertEquals(123456L, TgRequestContext.getChatId());
    }

    @Test
    void testInitContext_whenLanguageCodeIsEmpty_shouldSetDefaultLanguage() {
        // Given
        Update update = createUpdate("testuser", "", 123456L);

        // When
        TgRequestContext.initContext(update);

        // Then
        assertEquals("testuser", TgRequestContext.getUsername());
        assertEquals("en-US", TgRequestContext.getLanguageCode());
        assertEquals(123456L, TgRequestContext.getChatId());
    }

    private Update createUpdate(String username, String languageCode, Long chatId) {
        User user = new User();
        user.setUserName(username);
        user.setLanguageCode(languageCode);

        Chat chat = new Chat();
        chat.setId(chatId);

        Message message = new Message();
        message.setFrom(user);
        message.setChat(chat);

        Update update = new Update();
        update.setMessage(message);

        return update;
    }
}
