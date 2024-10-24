package org.github.akarkin1.config.exception;

public class S3DownloadFailureException extends RuntimeException {

  public S3DownloadFailureException(String bucket, String key, Throwable cause) {
    super("Failed to download S3 file: \"s3://%s/%s\"".formatted(bucket, key), cause);
  }

}
