package org.github.akarkin1.tg;

import org.github.akarkin1.translation.Translator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotCommunicatorTest {

    @Mock
    private AbsSender sender;

    @Mock
    private Translator translator;

    @InjectMocks
    private BotCommunicator botCommunicator;

    @Test
    void testSendMessageToTheBot_shouldSendTranslatedMessage() throws TelegramApiException {
        // Given
        Long chatId = 12345L;
        String languageCode = "en";
        String originalMessage = "Hello, %s!";
        String translatedMessage = "Hello, World!";
        Object[] params = {"World"};
        Message responseMessage = new Message();

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getChatId).thenReturn(chatId);
            mockedContext.when(TgRequestContext::getLanguageCode).thenReturn(languageCode);

            when(translator.translate(eq(languageCode), eq(originalMessage), eq(params)))
                .thenReturn(translatedMessage);
            when(sender.execute(any(SendMessage.class))).thenReturn(responseMessage);

            // When
            botCommunicator.sendMessageToTheBot(originalMessage, params);

            // Then
            verify(translator, times(1)).translate(languageCode, originalMessage, params);
            verify(sender, times(1)).execute(any(SendMessage.class));
        }
    }

    @Test
    void testSendMessageToTheBot_whenChatIdIsNull_shouldNotSendMessage() throws TelegramApiException {
        // Given
        String originalMessage = "Hello, %s!";
        Object[] params = {"World"};

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getChatId).thenReturn(null);

            // When
            botCommunicator.sendMessageToTheBot(originalMessage, params);

            // Then
            verify(translator, never()).translate(any(), any(), any());
            verify(sender, never()).execute(any(SendMessage.class));
        }
    }
}
