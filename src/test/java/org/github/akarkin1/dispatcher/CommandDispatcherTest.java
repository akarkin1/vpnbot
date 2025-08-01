package org.github.akarkin1.dispatcher;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.response.CommandResponse;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    @Mock
    private BotCommunicator botCommunicator;

    @Mock
    private Authorizer authorizer;

    @Mock
    private BotCommand<?> mockCommand;

    @Test
    void testHandle_whenValidCommand_shouldExecuteCommand() throws Exception {
        // Given
        Update update = createUpdate("/test command arg");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);

        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        doReturn(new TextCommandResponse("Success")).when(mockCommand).run(any());

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(mockCommand).run(List.of("command", "arg"));
            verify(botCommunicator).sendMessageToTheBot("Success");
        }
    }

    @Test
    void testHandle_whenEmptyResponse_shouldNotSendMessage() throws Exception {
        // Given
        Update update = createUpdate("/test");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);

        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        doReturn(createEmptyResponse()).when(mockCommand).run(any());

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(mockCommand).run(List.of());
            verify(botCommunicator, never()).sendMessageToTheBot(anyString());
        }
    }

    @Test
    void testHandle_whenUnknownCommand_shouldSendError() {
        // Given
        Update update = createUpdate("/unknown");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(botCommunicator).sendMessageToTheBot(contains("${common.command.not-supported.error}"), eq("/unknown"));
        }
    }

    @Test
    void testHandle_whenNotACommand_shouldSendError() {
        // Given
        Update update = createUpdate("not a command");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);

        // When
        dispatcher.handle(update);

        // Then
        verify(botCommunicator).sendMessageToTheBot("${common.syntax.not-supported.error}", "not a command");
    }

    @Test
    void testHandle_whenInvalidCommandException_shouldSendError() throws Exception {
        // Given
        Update update = createUpdate("/test");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);

        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        doThrow(new InvalidCommandException("Invalid syntax")).when(mockCommand).run(any());

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(botCommunicator).sendMessageToTheBot("Invalid command syntax: Invalid syntax");
        }
    }

    @Test
    void testHandle_whenUnauthorized_shouldSendError() throws Exception {
        // Given
        Update update = createUpdate("/test");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);
        Permission requiredPermission = Permission.ROOT_ACCESS;

        doReturn(List.of(requiredPermission)).when(mockCommand).getRequiredPermissions();
        when(authorizer.hasPermission("testuser", requiredPermission)).thenReturn(false);

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(botCommunicator).sendMessageToTheBot("${user.not-authorized.error}");
        }
    }

    @Test
    void testHandle_whenCommandExecutionFailed_shouldSendError() throws Exception {
        // Given
        Update update = createUpdate("/test");
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);

        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        doThrow(new CommandExecutionFailedException("Execution failed")).when(mockCommand).run(any());

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            dispatcher.handle(update);

            // Then
            verify(botCommunicator).sendMessageToTheBot("Execution failed");
        }
    }

    @Test
    void testGetSupportedCommands_shouldReturnAuthorizedCommands() {
        // Given
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);
        dispatcher.registerCommand("/test", mockCommand);

        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        when(mockCommand.getDescription()).thenReturn("Test command");

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");

            // When
            String result = dispatcher.getSupportedCommands();

            // Then
            assertTrue(result.contains("/test – Test command"));
        }
    }

    @Test
    void testRegisterCommand_shouldAddCommandToRegistry() throws Exception {
        // Given
        CommandDispatcher dispatcher = new CommandDispatcher(botCommunicator, authorizer);

        // When
        dispatcher.registerCommand("/newcommand", mockCommand);

        // Then - Verify command is registered by trying to execute it
        Update update = createUpdate("/newcommand");
        doReturn(Collections.emptyList()).when(mockCommand).getRequiredPermissions();
        doReturn(createEmptyResponse()).when(mockCommand).run(any());

        try (MockedStatic<TgRequestContext> mockedContext = mockStatic(TgRequestContext.class)) {
            mockedContext.when(TgRequestContext::getUsername).thenReturn("testuser");
            dispatcher.handle(update);
            verify(mockCommand).run(any());
        }
    }

    private Update createUpdate(String text) {
        Message message = new Message();
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);

        return update;
    }

    private CommandResponse createEmptyResponse() {
        return EmptyResponse.NONE;
    }
}
