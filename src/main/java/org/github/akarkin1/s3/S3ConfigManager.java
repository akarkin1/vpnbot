package org.github.akarkin1.s3;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.exception.S3DownloadFailureException;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class S3ConfigManager {

  private final S3Client s3Client;
  private final S3Configuration config;
  private final RequestMetrics metrics;

  public static S3ConfigManager create(S3Configuration config, RequestMetrics metrics) {
    S3Client createdClient = S3Client.create();
    return new S3ConfigManager(createdClient, config, metrics);
  }

  public String downloadConfigFromS3(String fileName) throws S3DownloadFailureException {
    return metrics.time(MetricComponent.S3, () -> download(fileName));
  }

  private String download(String fileName) {
    String bucket = config.getConfigBucket();
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucket)
        .key(joinPath(config.getConfigRootDir(), fileName))
        .build();
    try {
      ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(request);
      return IOUtils.toString(resp, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new S3DownloadFailureException(bucket, fileName, e);
    }
  }

  public static String joinPath(String... pathParts) {
    return String.join("/", pathParts);
  }

  public void uploadConfigToS3(String fileName, String content) {
    String bucket = config.getConfigBucket();
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(joinPath(config.getConfigRootDir(), fileName))
        .build();

    s3Client.putObject(request, RequestBody.fromString(content));
  }

}
