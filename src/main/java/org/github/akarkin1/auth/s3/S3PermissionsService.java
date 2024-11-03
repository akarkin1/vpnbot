package org.github.akarkin1.auth.s3;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserPermission;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.github.akarkin1.util.JsonUtils.parseJson;
import static org.github.akarkin1.util.JsonUtils.toJson;

@RequiredArgsConstructor
public class S3PermissionsService implements PermissionsService {

  private final S3ConfigManager s3ConfigManager;
  private final S3Configuration s3Config;

  @Override
  public Map<String, List<UserPermission>> getUserPermissions() {
    String fileContent = s3ConfigManager.downloadConfigFromS3(s3Config.getUserPermissionsKey());
    Map<String, List<String>> userPermissions = parseJson(fileContent,
                                                          new TypeReference<>() {});
    return userPermissions.entrySet()
        .stream()
        .map(entry -> Map.entry(entry.getKey(),
                                entry.getValue().stream().map(UserPermission::valueOf).toList()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  @Override
  public void addPermissionsTo(String tgUsername, List<UserPermission> actions) {
    Map<String, List<UserPermission>> userPermissions = new HashMap<>(this.getUserPermissions());
    userPermissions.computeIfAbsent(tgUsername, ignored -> new ArrayList<>())
        .addAll(actions);
    s3ConfigManager.uploadConfigToS3(s3Config.getUserPermissionsKey(), toJson(userPermissions));
  }

}
