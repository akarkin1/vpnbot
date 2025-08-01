package org.github.akarkin1.ju5ext;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class LambdaTestEnvironment implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        System.setProperty("S3_CONFIG_BUCKET", "s3-config-test-bucket");
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        System.setProperty("S3_CONFIG_BUCKET", "");
    }
}
