package org.github.akarkin1.auth.s3;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.auth.UserEntitlements.Entitlement;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.*;

import static org.github.akarkin1.util.JsonUtils.parseJson;
import static org.github.akarkin1.util.JsonUtils.toJson;

@RequiredArgsConstructor
public class S3EntitlementsService implements EntitlementsService {

  private final S3ConfigManager s3ConfigManager;
  private final S3Configuration s3Config;

  @Override
  public Map<String, List<Entitlement>> getUserEntitlements() {
    String fileContent = s3ConfigManager.downloadConfigFromS3(s3Config.getUserPermissionsKey());
    UserEntitlements userEntitlements = parseJson(fileContent,
                                                          UserEntitlements.class);
    return userEntitlements.getUserEntitlements();
  }

  @Override
  public void updateUserEntitlements(String tgUsername, Collection<Entitlement> newEntitlements) {
    Map<String, List<Entitlement>> curUserPermissions = new HashMap<>(this.getUserEntitlements());
    if (CollectionUtils.isNullOrEmpty(newEntitlements)) {
      // delete the user
      curUserPermissions.remove(tgUsername);
    } else {
      curUserPermissions.put(tgUsername, new ArrayList<>(newEntitlements));
    }

    UserEntitlements userEntitlements = new UserEntitlements();
    userEntitlements.setUserEntitlements(curUserPermissions);

    s3ConfigManager.uploadConfigToS3(s3Config.getUserPermissionsKey(), toJson(userEntitlements));
  }

}
