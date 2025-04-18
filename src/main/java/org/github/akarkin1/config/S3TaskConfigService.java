package org.github.akarkin1.config;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.exception.S3DownloadFailureException;
import org.github.akarkin1.config.model.CfnStackOutputParameter;
import org.github.akarkin1.config.model.StackOutputParameters;
import org.github.akarkin1.s3.S3ConfigManager;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.github.akarkin1.s3.S3ConfigManager.joinPath;
import static org.github.akarkin1.util.JsonUtils.parseJson;

@Log4j2
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class S3TaskConfigService implements TaskConfigService {
  private static final List<Region> AWS_REGIONS = new ArrayList<>(Region.regions());
  static {
    AWS_REGIONS.add(Region.of("ap-southeast-7"));
  }

  private static final Map<String, Region> KNOWN_REGIONS = AWS_REGIONS
      .stream()
      .collect(Collectors.toMap(Region::id, r -> r));

  private static final String AWS_LINE_SEPARATOR = "\n";

  private final S3ConfigManager s3ConfigManager;
  private final S3Configuration config;

  public static S3TaskConfigService create(S3Configuration config) {
    S3ConfigManager s3ConfigManager = S3ConfigManager.create(config);
    return new S3TaskConfigService(s3ConfigManager, config);
  }

  @Override
  public List<Region> getSupportedRegions() throws S3DownloadFailureException {
    String regionsContent = s3ConfigManager.downloadConfigFromS3(config.getRegionsKey());
    return parseRegions(regionsContent);
  }

  private static List<Region> parseRegions(String content) {
    return Stream.of(content.split(AWS_LINE_SEPARATOR))
        .map(String::trim)
        .filter(regionId -> {
          if (!KNOWN_REGIONS.containsKey(regionId)) {
            log.warn("Region id {} is not known to AWS SDK", regionId);
            return false;
          }

          return true;
        })
        .map(KNOWN_REGIONS::get)
        .toList();
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region)
      throws S3DownloadFailureException {

    String jsonContent = s3ConfigManager.downloadConfigFromS3(
        joinPath(region.id(), config.getStackOutputParametersKey()));
    List<CfnStackOutputParameter> outputParameters = parseJson(jsonContent,
                                                               new TypeReference<>() {});

    val runtimeParamBuilder = TaskRuntimeParameters.builder();
    for (CfnStackOutputParameter stackOutParam : outputParameters) {
      String outParamKey = stackOutParam.outputKey();
      String outParamValue = stackOutParam.outputValue();
      if (StackOutputParameters.ECS_CLUSTER_NAME.equals(outParamKey)) {
        runtimeParamBuilder.ecsClusterName(outParamValue);
      } else if (StackOutputParameters.ECS_TASK_DEFINITION.equals(outParamKey)) {
        runtimeParamBuilder.ecsTaskDefinition(outParamValue);
      } else if (StackOutputParameters.SECURITY_GROUP_ID.equals(outParamKey)) {
        runtimeParamBuilder.securityGroupId(outParamValue);
      } else if (StackOutputParameters.SUBNET_ID.equals(outParamKey)) {
        runtimeParamBuilder.subnetId(outParamValue);
      }
    }
    return runtimeParamBuilder.build();

  }

}
