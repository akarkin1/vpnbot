package org.github.akarkin1.auth.s3;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;

public class EntitlementsServiceConfigurer {

  public EntitlementsService configure() {
    S3Configuration s3 = ConfigManager.getApplicationYaml().getS3();
    S3ConfigManager s3ConfigManager = S3ConfigManager.create(s3);
    S3EntitlementsService s3PermissionsService = new S3EntitlementsService(s3ConfigManager, s3);

    return new CachingEntitlementsService(s3PermissionsService);
  }

}
