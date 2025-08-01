package org.github.akarkin1.s3;

import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.exception.S3DownloadFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ConfigManagerTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Configuration config;

    @Test
    void testCreate_shouldReturnS3ConfigManager() {
        // Given
        S3Configuration configuration = mock(S3Configuration.class);

        // When
        S3ConfigManager manager = S3ConfigManager.create(configuration);

        // Then
        assertNotNull(manager);
    }

    @Test
    void testDownloadConfigFromS3_shouldReturnFileContent() throws Exception {
        // Given
        String fileName = "test-config.yml";
        String bucket = "test-bucket";
        String rootDir = "configs";
        String expectedContent = "test: value";

        S3ConfigManager manager = createS3ConfigManager();

        when(config.getConfigBucket()).thenReturn(bucket);
        when(config.getConfigRootDir()).thenReturn(rootDir);

        ResponseInputStream<GetObjectResponse> responseStream = createResponseInputStream(expectedContent);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        // When
        String result = manager.downloadConfigFromS3(fileName);

        // Then
        assertEquals(expectedContent, result);
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void testDownloadConfigFromS3_whenS3ThrowsException_shouldThrowS3DownloadFailureException() {
        // Given
        String fileName = "test-config.yml";
        String bucket = "test-bucket";
        String rootDir = "configs";

        S3ConfigManager manager = createS3ConfigManager();

        when(config.getConfigBucket()).thenReturn(bucket);
        when(config.getConfigRootDir()).thenReturn(rootDir);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(new RuntimeException("S3 error"));

        // When & Then
        assertThrows(S3DownloadFailureException.class, () -> {
            manager.downloadConfigFromS3(fileName);
        });
    }

    @Test
    void testUploadConfigToS3_shouldCallS3Client() {
        // Given
        String fileName = "test-config.yml";
        String content = "test: value";
        String bucket = "test-bucket";
        String rootDir = "configs";

        S3ConfigManager manager = createS3ConfigManager();

        when(config.getConfigBucket()).thenReturn(bucket);
        when(config.getConfigRootDir()).thenReturn(rootDir);

        // When
        manager.uploadConfigToS3(fileName, content);

        // Then
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testJoinPath_shouldJoinPathParts() {
        // Given
        String[] pathParts = {"root", "sub", "file.txt"};

        // When
        String result = S3ConfigManager.joinPath(pathParts);

        // Then
        assertEquals("root/sub/file.txt", result);
    }

    @Test
    void testJoinPath_withSinglePart_shouldReturnSinglePart() {
        // Given
        String[] pathParts = {"file.txt"};

        // When
        String result = S3ConfigManager.joinPath(pathParts);

        // Then
        assertEquals("file.txt", result);
    }

    @Test
    void testJoinPath_withEmptyArray_shouldReturnEmptyString() {
        // Given
        String[] pathParts = {};

        // When
        String result = S3ConfigManager.joinPath(pathParts);

        // Then
        assertEquals("", result);
    }

    private S3ConfigManager createS3ConfigManager() {
        try {
            // Use reflection to create S3ConfigManager with mocked dependencies
            var constructor = S3ConfigManager.class.getDeclaredConstructor(S3Client.class, S3Configuration.class);
            constructor.setAccessible(true);
            return constructor.newInstance(s3Client, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseInputStream<GetObjectResponse> createResponseInputStream(String content) {
        GetObjectResponse response = GetObjectResponse.builder().build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        return new ResponseInputStream<>(response, inputStream);
    }
}
