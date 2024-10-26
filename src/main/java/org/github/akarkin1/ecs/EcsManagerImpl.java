package org.github.akarkin1.ecs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
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
import software.amazon.awssdk.services.ecs.model.TaskField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
public class EcsManagerImpl implements EcsManager {

  private static final String CONTAINER_NAME = "vpn-container";
  private static final String ELASTIC_NETWORK_INTERFACE_FIELD = "ElasticNetworkInterface";
  private static final String NETWORK_INTERFACE_ID = "networkInterfaceId";

  private final TaskConfigService taskConfigService;
  private final EcsClientPool ecsClientPool;
  private final Ec2ClientPool ec2ClientPool;
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

    for (Region region : taskConfigService.getSupportedRegions()) {
      TaskRuntimeParameters taskRuntimeParameters = taskConfigService.getTaskRuntimeParameters(
          region);
      String clusterName = taskRuntimeParameters.getEcsClusterName();
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
        String hostName = task.tags()
            .stream()
            .filter(tag -> config.getHostNameTag().equals(tag.key()))
            .map(Tag::value)
            .findFirst()
            .orElse(null);

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
  public Set<String> getSupportedRegions() {
    return taskConfigService.getSupportedRegions()
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

          String hostName = task.tags()
              .stream()
              .filter(tag -> config.getHostNameTag().equals(tag.key()))
              .map(Tag::value)
              .findFirst()
              .orElse(null);

          return TaskInfo.builder()
              .hostName(hostName)
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

}
