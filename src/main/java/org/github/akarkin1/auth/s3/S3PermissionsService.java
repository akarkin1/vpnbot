package org.github.akarkin1.auth.s3;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserAction;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.github.akarkin1.util.JsonUtils.parseMapOfListsSilently;
import static org.github.akarkin1.util.JsonUtils.toJson;

@RequiredArgsConstructor
public class S3PermissionsService implements PermissionsService {

  private final S3ConfigManager s3ConfigManager;
  private final S3Configuration s3Config;

  @Override
  public Map<String, List<String>> getUserPermissions() {
    String fileContent = s3ConfigManager.downloadConfigFromS3(s3Config.getUserPermissionsKey());
    return parseMapOfListsSilently(fileContent);
  }

  @Override
  public void addPermissionsTo(String tgUsername, List<UserAction> actions) {
    Map<String, List<String>> userPermissions = new HashMap<>(this.getUserPermissions());
    userPermissions.computeIfAbsent(tgUsername, ignored -> new ArrayList<>())
        .addAll(actions.stream().map(UserAction::name).toList());
    s3ConfigManager.uploadConfigToS3(s3Config.getUserPermissionsKey(), toJson(userPermissions));
  }

}
