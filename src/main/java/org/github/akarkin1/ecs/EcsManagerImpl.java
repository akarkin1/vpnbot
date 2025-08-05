package org.github.akarkin1.ecs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.TaskRuntimeParameters;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsContainerHealth;
import org.github.akarkin1.ec2.Ec2ClientPool;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.NetworkInterface;
import software.amazon.awssdk.services.ec2.model.NetworkInterfaceAssociation;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.HealthStatus;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskField;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.awssdk.utils.MapUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EcsManagerImpl implements EcsManager {

  private static final Logger log = LogManager.getLogger(EcsManagerImpl.class);

  private static final String ELASTIC_NETWORK_INTERFACE_FIELD = "ElasticNetworkInterface";
  private static final String NETWORK_INTERFACE_ID = "networkInterfaceId";

  private final TaskConfigService taskConfigService;
  private final EcsClientPool ecsClientPool;
  private final Ec2ClientPool ec2ClientPool;
  private final EcsConfiguration config;
  private final Map<String, String> regionToCitiesMap;

  @Override
  public TaskInfo startTask(Region region, String hostName, String serviceName, Map<String, String> tags,
                            List<String> additionalArgs) {
    TaskRuntimeParameters taskParams = taskConfigService.getTaskRuntimeParameters(region, serviceName);
    AwsVpcConfiguration awsVpcConfig = AwsVpcConfiguration.builder()
        .assignPublicIp(AssignPublicIp.ENABLED)
        .securityGroups(taskParams.getSecurityGroupId())
        .subnets(taskParams.getSubnetId())
        .build();
    NetworkConfiguration networkConfig = NetworkConfiguration.builder()
        .awsvpcConfiguration(awsVpcConfig)
        .build();

    Map<String, String> envVarsValues = new HashMap<>();
    envVarsValues.putAll(tags);
    envVarsValues.putAll(parseAdditionalArgs(additionalArgs));
    ContainerOverride containerOverride = ContainerOverride.builder()
        .name(config.getEssentialContainerName())
        .environment(buildEnvs(config.getServiceEnv().get(serviceName).getEnvVars(), envVarsValues))
        .build();

    RunTaskRequest runTaskRequest = RunTaskRequest.builder()
        .tags(toEcsTags(tags))
        .cluster(taskParams.getEcsClusterName())
        .taskDefinition(taskParams.getEcsTaskDefinition())
        .count(1)
        .launchType(LaunchType.FARGATE)
        .overrides(overrides -> overrides.containerOverrides(containerOverride).build())
        .networkConfiguration(networkConfig)
        .build();

    EcsClient client = ecsClientPool.get(region);

    RunTaskResponse resp = client.runTask(runTaskRequest);
    if (!resp.hasTasks() || resp.tasks().isEmpty()) {
      throw new RuntimeException("No tasks started. Failures: %s".formatted(resp.failures()));
    }

    Task task = resp.tasks().getFirst();

    return TaskInfo.builder()
        .id(taskIdFromArn(task.taskArn()))
        .cluster(taskParams.getEcsClusterName())
        .region(region)
        .build();
  }

  private Map<String, String> parseAdditionalArgs(List<String> additionalArgs) {
    if (CollectionUtils.isNullOrEmpty(additionalArgs)) {
      return Collections.emptyMap();
    }

    return additionalArgs.stream()
      .filter(val -> val.strip().matches("^.*?=.*?$"))
      .map(val -> {
        String[] parts = val.split("=", 2);
        return Map.entry(parts[0].strip(), parts[1].strip());
      })
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Collection<KeyValuePair> buildEnvs(Map<String, String> envVars,
                                             Map<String, String> replacements) {
    return envVars.entrySet()
      .stream()
      .map(entry -> entry.getValue().matches("^\\$\\{.*?}$")
        ? env(entry.getKey(), replacePlaceholders(entry.getValue(), replacements))
        : env(entry.getKey(), entry.getValue()))
      .filter(kvPair -> StringUtils.isNotBlank(kvPair.value()))
      .toList();
  }

  private static String replacePlaceholders(String value, Map<String, String> replacements) {
    return new StringSubstitutor(replacements).replace(value);
  }

  @Override
  @SneakyThrows(InterruptedException.class)
  public RunTaskStatus checkTaskHealth(Region region, String clusterName, String taskId) {
    EcsContainerHealth health = config.getHealth();

    DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
        .cluster(clusterName)
        .tasks(taskId)
        .build();
    EcsClient client = ecsClientPool.get(region);
    long startedAt = System.currentTimeMillis();
    RunTaskStatus lastStatus = RunTaskStatus.UNKNOWN;

    for (; ; ) {
      long timePassed = System.currentTimeMillis() - startedAt;

      if (timePassed >= TimeUnit.SECONDS.toMillis(health.getTimeoutSec())) {
        return lastStatus;
      }

      DescribeTasksResponse resp = client.describeTasks(describeTasksRequest);
      List<Task> runningTasks = resp.tasks();
      if (!resp.hasTasks() || runningTasks.isEmpty()) {
        log.warn("No tasks found. Retrying...");
        TimeUnit.MILLISECONDS.sleep(health.getIntervalMs());
        continue;
      }
      if (resp.hasFailures() && !resp.failures().isEmpty()) {
        log.error("Failed to get list of tasks. Failures: ");
        resp.failures().forEach(log::error);
        return RunTaskStatus.UNHEALTHY;
      }

      Task task = runningTasks.getFirst();
      if (!HealthStatus.HEALTHY.equals(task.healthStatus())) {
        TimeUnit.MILLISECONDS.sleep(health.getIntervalMs());
        continue;
      }

      lastStatus = getContainerHealthStatus(task);
      if (RunTaskStatus.HEALTHY.equals(lastStatus)) {
        return RunTaskStatus.HEALTHY;
      } else {
        TimeUnit.MILLISECONDS.sleep(health.getIntervalMs());
      }
    }
  }

  private RunTaskStatus getContainerHealthStatus(Task task) {
    return task.containers()
        .stream()
        .filter(container -> container.name().equals(config.getEssentialContainerName()))
        .map(container -> RunTaskStatus.valueOf(container.healthStatus().name()))
        .findFirst()
        .orElse(RunTaskStatus.UNKNOWN);

  }

  private List<Tag> toEcsTags(Map<String, String> tags) {
    return tags.entrySet()
        .stream()
        .map(entry -> Tag.builder()
            .key(entry.getKey())
            .value(entry.getValue())
            .build())
        .toList();
  }

  private static KeyValuePair env(String name, String value) {
    return KeyValuePair.builder()
        .name(name)
        .value(value)
        .build();
  }

  @Override
  public List<TaskInfo> listTasks(Map<String, String> matchingTags) {
    List<TaskInfo> foundTasks = new ArrayList<>();

    for (String supportedService : taskConfigService.getSupportedServices()) {
      for (Region region : taskConfigService.getSupportedRegions(supportedService)) {
        TaskRuntimeParameters runtimeParams = taskConfigService.getTaskRuntimeParameters(region, supportedService);
        String clusterName = runtimeParams.getEcsClusterName();
        EcsClient client = ecsClientPool.get(region);
        ListTasksRequest listTasksRequest = ListTasksRequest.builder()
            .cluster(clusterName)
            .build();
        ListTasksResponse listTasksResponse = client.listTasks(listTasksRequest);
        List<String> taskArns = listTasksResponse.taskArns();
        if (taskArns.isEmpty()) {
          continue;
        }

        DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
            .cluster(clusterName)
            .tasks(taskArns)
            .include(TaskField.TAGS)
            .build();

        DescribeTasksResponse describeTaskResp = client.describeTasks(describeTasksRequest);
        for (Task task : describeTaskResp.tasks()) {
          log.debug("Task tags: {}, checking tags: {}", task.tags(), matchingTags);
          boolean tagMissmatch = task.tags()
              .stream()
              .anyMatch(tag -> matchingTags.containsKey(tag.key())
                               && !matchingTags.get(tag.key()).equals(tag.value()));

          if (tagMissmatch) {
            continue;
          }

          log.debug("Task attachments: {}", task.attachments());

          String publicIp = getTaskPublicIp(region, task);

          log.debug("Task tags: {}, Hostname tag name: {}", task.tags(),
                    config.getHostNameTag());
          String hostName = extractTag(task, config.getHostNameTag());

          TaskInfo taskInfo = TaskInfo.builder()
              .hostName(hostName)
              .id(taskIdFromArn(task.taskArn()))
              .state(getContainerHealthStatus(task).name())
              .cluster(clusterName)
              .region(region)
              .location(regionToCitiesMap.get(region.id()))
              .publicIp(publicIp)
              .build();

          foundTasks.add(taskInfo);
        }
      }
    }

    return foundTasks;
  }

  private String getTaskPublicIp(Region region, Task task) {
    return task.attachments()
        .stream()
        .filter(attachment -> ELASTIC_NETWORK_INTERFACE_FIELD.equals(attachment.type()))
        .map(Attachment::details)
        .flatMap(Collection::stream)
        .filter(detail -> NETWORK_INTERFACE_ID.equals(detail.name()))
        .map(KeyValuePair::value)
        .findFirst()
        .map(networkInterfaceId -> this.fetchPublicIp(region, networkInterfaceId))
        .orElse(null);
  }

  private String fetchPublicIp(Region region, String networkInterfaceId) {
    Ec2Client ec2Client = ec2ClientPool.getForRegion(region.id());
    DescribeNetworkInterfacesRequest request = DescribeNetworkInterfacesRequest.builder()
        .networkInterfaceIds(networkInterfaceId)
        .build();
    DescribeNetworkInterfacesResponse response = ec2Client.describeNetworkInterfaces(request);
    return response.networkInterfaces().stream()
        .map(NetworkInterface::association)
        .map(NetworkInterfaceAssociation::publicIp)
        .findFirst()
        .orElse(null);
  }

  private static String taskIdFromArn(String taskArn) {
    // taskArn: arn:aws:ecs:<region>:<account-id>:task/<cluster-name>/<task-id>
    return taskArn.substring(taskArn.lastIndexOf('/') + 1);
  }

  @Override
  public Set<String> getSupportedRegions(String serviceName) {
    return taskConfigService.getSupportedRegions(serviceName)
        .stream()
        .map(Region::id)
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<TaskInfo> getFullTaskInfo(Region region, String clusterName, String taskId) {
    EcsClient client = ecsClientPool.get(region);

    DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
        .cluster(clusterName)
        .tasks(taskId)
        .include(TaskField.TAGS)
        .build();

    DescribeTasksResponse describeTaskResp = client.describeTasks(describeTasksRequest);

    return describeTaskResp.tasks().stream()
        .map(task -> {
          String publicIp = getTaskPublicIp(region, task);

          String hostName = extractTag(task, config.getHostNameTag());
          String serviceName = extractTag(task, config.getServiceNameTag());

          return TaskInfo.builder()
              .hostName(hostName)
              .serviceName(serviceName)
              .id(taskId)
              .state(getContainerHealthStatus(task).name())
              .cluster(clusterName)
              .region(region)
              .location(regionToCitiesMap.get(region.id()))
              .publicIp(publicIp)
              .build();
        })
        .findFirst();
  }

  private String extractTag(Task task, String tagName) {
    return task.tags()
        .stream()
        .filter(tag -> tagName.equals(tag.key()))
        .map(Tag::value)
        .findFirst()
        .orElse(null);
  }

}
