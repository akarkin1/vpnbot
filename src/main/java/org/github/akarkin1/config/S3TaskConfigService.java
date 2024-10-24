package org.github.akarkin1.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.github.akarkin1.config.TaskRuntimeParameters.TaskRuntimeParametersBuilder;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.exception.S3DownloadFailureException;
import org.github.akarkin1.config.model.CfnStackOutputParameter;
import org.github.akarkin1.config.model.StackOutputParameters;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class S3TaskConfigService implements TaskConfigService {

  private static final Map<String, Region> KNOWN_REGIONS = Region.regions()
      .stream()
      .collect(Collectors.toMap(Region::id, r -> r));
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String AWS_LINE_SEPARATOR = "\n";

  private final S3Client s3Client;
  private final S3Configuration config;

  public static S3TaskConfigService create(S3Configuration config) {
    S3Client createdClient = S3Client.create();
    return new S3TaskConfigService(createdClient, config);
  }

  @Override
  public List<Region> getSupportedRegions() throws S3DownloadFailureException {
    String regionsContent = downloadConfigFromS3(config.getRegionsKey());
    return parseRegions(regionsContent);
  }

  private String downloadConfigFromS3(String fileName) throws S3DownloadFailureException {
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

  private static String joinPath(String... pathParts) {
    return String.join("/", pathParts);
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region)
      throws S3DownloadFailureException {

    String jsonContent = downloadConfigFromS3(joinPath(region.id(),
                                                       config.getStackOutputParametersKey()));
    try {
      List<CfnStackOutputParameter> outputParameters = MAPPER.readValue(jsonContent,
                                                                        new TypeReference<>() {
                                                                        });

      TaskRuntimeParametersBuilder runtimeParamBuilder = TaskRuntimeParameters.builder();
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
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to parse json file: %s"
                                     .formatted(config.getStackOutputParametersKey()), e);
    }
  }

}
