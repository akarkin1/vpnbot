package org.github.akarkin1.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class CommandExecutionFailedExceptionTest {

    @Test
    void testConstructor_shouldCreateExceptionWithMessage() {
        // Given
        String errorMessage = "Command execution failed";

        // When
        CommandExecutionFailedException exception = new CommandExecutionFailedException(errorMessage);

        // Then
        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    void testThrowException_shouldBeRuntimeException() {
        // Given
        String errorMessage = "Command execution failed";

        // When & Then
        RuntimeException exception = assertThrows(CommandExecutionFailedException.class, () -> {
            throw new CommandExecutionFailedException(errorMessage);
        });
        assertEquals(errorMessage, exception.getMessage());
    }
}
