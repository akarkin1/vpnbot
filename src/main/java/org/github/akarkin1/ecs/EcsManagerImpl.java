package org.github.akarkin1.ecs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.TaskRuntimeParameters;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsContainerHealth;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.Container;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EcsManagerImpl implements EcsManager {

  private static final String CONTAINER_NAME = "vpn-container";
  private static final String ELASTIC_NETWORK_INTERFACE_FIELD = "ElasticNetworkInterface";
  private static final String PUBLIC_IP_FIELD = "publicIp";

  private final TaskConfigService taskConfigService;
  private final EcsClientPool clientPool;
  private final EcsConfiguration config;
  private final Map<String, String> regionToCitiesMap;

  @Override
  public TaskInfo startTask(Region region, String hostName, Map<String, String> tags) {
    TaskRuntimeParameters taskParams = taskConfigService.getTaskRuntimeParameters(region);
    AwsVpcConfiguration awsVpcConfig = AwsVpcConfiguration.builder()
        .assignPublicIp(AssignPublicIp.ENABLED)
        .securityGroups(taskParams.getSecurityGroupId())
        .subnets(taskParams.getSubnetId())
        .build();
    NetworkConfiguration networkConfig = NetworkConfiguration.builder()
        .awsvpcConfiguration(awsVpcConfig)
        .build();

    ContainerOverride containerOverride = ContainerOverride.builder()
        .name(CONTAINER_NAME)
        .environment(env(config.getHostNameEnv(), hostName))
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

    EcsClient client = clientPool.get(region);

    RunTaskResponse resp = client.runTask(runTaskRequest);
    if (!resp.hasTasks() || resp.tasks().isEmpty()) {
      throw new RuntimeException("No tasks started. Failures: %s".formatted(resp.failures()));
    }

    Task task = resp.tasks().getFirst();

    return TaskInfo.builder()
        .state(task.lastStatus())
        .id(taskIdFromArn(task.taskArn()))
        .cluster(taskParams.getEcsClusterName())
        .region(region)
        .location(regionToCitiesMap.get(region.id()))
        .build();
  }

  @Override
  @SneakyThrows(InterruptedException.class)
  public RunTaskStatus checkTaskHealth(Region region, String clusterName, String taskId) {
    EcsContainerHealth health = config.getHealth();

    DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
        .cluster(clusterName)
        .tasks(taskId)
        .build();
    EcsClient client = clientPool.get(region);
    long startedAt = System.currentTimeMillis();
    RunTaskStatus lastStatus = RunTaskStatus.UNKNOWN;

    for (; ; ) {
      long timePassed = System.currentTimeMillis() - startedAt;

      if (timePassed >= TimeUnit.SECONDS.toMillis(health.getTimeoutSec())) {
        return lastStatus;
      }

      DescribeTasksResponse resp = client.describeTasks(describeTasksRequest);
      if (!resp.hasTasks() || resp.tasks().isEmpty() || resp.hasFailures()) {
        throw new RuntimeException("Failed to check task status: " + taskId);
      }

      Task task = resp.tasks().getFirst();
      if (!HealthStatus.HEALTHY.equals(task.healthStatus())) {
        TimeUnit.MILLISECONDS.sleep(health.getIntervalMs());
        continue;
      }

      Container container = task.containers().getFirst();
      lastStatus = RunTaskStatus.valueOf(container.healthStatus().name());
      if (HealthStatus.HEALTHY.equals(container.healthStatus())) {
        return RunTaskStatus.HEALTHY;
      } else {
        TimeUnit.MILLISECONDS.sleep(health.getIntervalMs());
      }
    }
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

    for (Region region : taskConfigService.getSupportedRegions()) {
      TaskRuntimeParameters taskRuntimeParameters = taskConfigService.getTaskRuntimeParameters(
          region);
      String clusterName = taskRuntimeParameters.getEcsClusterName();
      EcsClient client = clientPool.get(region);
      ListTasksRequest listTasksRequest = ListTasksRequest.builder()
          .cluster(clusterName)
          .build();
      ListTasksResponse listTasksResponse = client.listTasks(listTasksRequest);
      List<String> taskArns = listTasksResponse.taskArns();

      DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
          .cluster(clusterName)
          .tasks(taskArns)
          .build();

      DescribeTasksResponse describeTaskResp = client.describeTasks(describeTasksRequest);
      for (Task task : describeTaskResp.tasks()) {
        boolean tagsMissmatch = task.tags()
            .stream()
            .anyMatch(tag -> !matchingTags.containsKey(tag.key())
                             || !matchingTags.get(tag.key()).equals(tag.value()));

        if (tagsMissmatch) {
          continue;
        }

        String publicIp = task.attachments()
            .stream()
            .filter(attachment -> ELASTIC_NETWORK_INTERFACE_FIELD.equals(attachment.type()))
            .map(Attachment::details)
            .flatMap(Collection::stream)
            .filter(detail -> PUBLIC_IP_FIELD.equals(detail.name()))
            .map(KeyValuePair::value)
            .findFirst()
            .orElse(null);

        String hostName = task.tags()
            .stream()
            .filter(tag -> config.getHostNameTag().equals(tag.key()))
            .map(Tag::value)
            .findFirst()
            .orElse(null);

        TaskInfo taskInfo = TaskInfo.builder()
            .hostName(hostName)
            .id(taskIdFromArn(task.taskArn()))
            .state(task.lastStatus())
            .cluster(clusterName)
            .region(region)
            .location(regionToCitiesMap.get(region.id()))
            .publicIp(publicIp)
            .build();

        foundTasks.add(taskInfo);
      }
    }

    return foundTasks;
  }

  private static String taskIdFromArn(String taskArn) {
    // taskArn: arn:aws:ecs:<region>:<account-id>:task/<cluster-name>/<task-id>
    return taskArn.substring(taskArn.lastIndexOf('/') + 1);
  }

  @Override
  public Set<String> getSupportedRegions() {
    return taskConfigService.getSupportedRegions()
        .stream()
        .map(Region::id)
        .collect(Collectors.toSet());
  }
}
