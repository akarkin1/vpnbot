package org.github.akarkin1.s3;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.exception.S3DownloadFailureException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class S3ConfigManager {

  private final ConfigManager configManager;
  private final S3Client s3Client;
  private final S3Configuration config;

  public static S3ConfigManager create(S3Configuration config, S3Client createdClient, ConfigManager configManager) {
    return new S3ConfigManager(createdClient, config, configManager);
  }

  private S3ConfigManager(S3Client s3Client, S3Configuration config, ConfigManager configManager) {
    this.s3Client = s3Client;
    this.config = config;
    this.configManager = configManager;
  }

  public String downloadConfigFromS3(String fileName) throws S3DownloadFailureException {
    String bucket = configManager.getS3ConfigBucket();
    String fullFilePath = joinPath(config.getConfigRootDir(), fileName);
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucket)
        .key(fullFilePath)
        .build();
    try {
      ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(request);
      return IOUtils.toString(resp, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new S3DownloadFailureException(bucket, fullFilePath, e);
    }
  }

  public static String joinPath(String... pathParts) {
    return String.join("/", pathParts);
  }

  public void uploadConfigToS3(String fileName, String content) {
    String bucket = configManager.getS3ConfigBucket();
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(joinPath(config.getConfigRootDir(), fileName))
        .build();

    s3Client.putObject(request, RequestBody.fromString(content));
  }

}
