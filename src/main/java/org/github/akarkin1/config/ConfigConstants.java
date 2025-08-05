package org.github.akarkin1.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigConstants {
    public final String APP_CONFIG_YAML;
    public final String EVENT_ROOT_DIR;
    public final String BOT_TOKEN_ENV;
    public final String BOT_TOKEN_PROP;
    public final String BOT_USERNAME_ENV;
    public final String BOT_SECRET_TOKEN_ID_ENV;
    public final String BOT_SECRET_TOKEN_ID_PROP;
    public final String REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV;
    public final String S3_CONFIG_BUCKET_ENV;
    public final String S3_CONFIG_BUCKET_PROP;
    public final String BOT_API_BASE_URL_PROP;

    public ConfigConstants() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config-constants.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config-constants.properties", e);
        }
        APP_CONFIG_YAML = props.getProperty("app.config.yaml", "application.yml");
        EVENT_ROOT_DIR = props.getProperty("event.root.dir", "/mnt/efs/eventIds");
        BOT_TOKEN_ENV = props.getProperty("bot.token.env", "BOT_TOKEN");
        BOT_TOKEN_PROP = props.getProperty("bot.token.prop", "tg.bot.token");
        BOT_USERNAME_ENV = props.getProperty("bot.username.env", "BOT_USERNAME");
        BOT_SECRET_TOKEN_ID_ENV = props.getProperty("bot.secret.token.id.env", "BOT_SECRET_TOKEN_ID");
        BOT_SECRET_TOKEN_ID_PROP = props.getProperty("bot.secret.token.id.prop", "bot.secret.token.id");
        REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV = props.getProperty("registered.event.expiration.time.sec.env", "REGISTERED_EVENT_EXPIRATION_TIME_SEC");
        S3_CONFIG_BUCKET_ENV = props.getProperty("s3.config.bucket.env", "S3_CONFIG_BUCKET");
        S3_CONFIG_BUCKET_PROP = props.getProperty("s3.config.bucket.prop", "s3.config.bucket");
        BOT_API_BASE_URL_PROP = props.getProperty("bot.api.base.url.prop", "tg.bot.api.base.url");
    }
}

