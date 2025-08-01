package org.github.akarkin1.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class InvalidCommandExceptionTest {

    @Test
    void testConstructor_shouldCreateExceptionWithMessage() {
        // Given
        String errorMessage = "Invalid command provided";

        // When
        InvalidCommandException exception = new InvalidCommandException(errorMessage);

        // Then
        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    void testThrowException_shouldBeRuntimeException() {
        // Given
        String errorMessage = "Invalid command provided";

        // When & Then
        RuntimeException exception = assertThrows(InvalidCommandException.class, () -> {
            throw new InvalidCommandException(errorMessage);
        });
        assertEquals(errorMessage, exception.getMessage());
    }
}
