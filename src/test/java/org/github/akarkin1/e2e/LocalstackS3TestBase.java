package org.github.akarkin1.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
public abstract class LocalstackS3TestBase {
    protected static final String BUCKET_NAME = "test-bucket";
    private static final String TEST_SECRET_TOKEN_ID = "BOT_SECRET_TOKEN_ID123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11";

    @Container
    public static LocalStackContainer localstack;

    @Container
    public static TelegramBotApiContainer telegramBot;

    protected static S3Client s3Client;


    static {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
            .withServices(LocalStackContainer.Service.S3);
        localstack.start();
        telegramBot = new TelegramBotApiContainer()
          // ToDo: Remove default values for API ID and API Hash
          .withEnv("TELEGRAM_API_ID", System.getProperty("telegram.api.id", "123456"))
          .withEnv("TELEGRAM_API_HASH", System.getProperty("telegram.api.hash", "0832d75cbf06531cc72eab342b147529"));
        telegramBot.start();
        s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .region(Region.of(localstack.getRegion()))
                .build();

        System.setProperty("bot.secret.token.id", TEST_SECRET_TOKEN_ID);
        System.setProperty("s3.config.bucket", BUCKET_NAME);

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (Exception e) {
            // ignore if already exists
        }
    }
}

