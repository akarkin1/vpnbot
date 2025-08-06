package org.github.akarkin1.ecs;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.github.akarkin1.util.JsonUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.secretsmanager.model.InternalServiceErrorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class EcsClientMock implements EcsClient {

  private static final String DEFAULT_REGION = "us-east-1";
  private static final String ROOT_DIR = ".test-data/ecs/";
  private static final String TASKS_DIR = ROOT_DIR + "tasks/";
  private static final String RUNNING_TASKS_FILE = ROOT_DIR + "running-tasks.json";

  private final AtomicInteger taskCounter = new AtomicInteger(0);

  private final String region;
  private final boolean isGlobal;

  public EcsClientMock() {
    initializeDirectories();
    this.region = DEFAULT_REGION;
    this.isGlobal = true;
  }

  public EcsClientMock(String region) {
    this.region = region;
    this.isGlobal = false;
    initializeDirectories();
  }

  private void initializeDirectories() {
    try {
      Files.createDirectories(Paths.get(TASKS_DIR));
      // Initialize running tasks file if it doesn't exist
      Path runningTasksPath = Paths.get(RUNNING_TASKS_FILE);
      if (!Files.exists(runningTasksPath)) {
        Files.writeString(runningTasksPath, "[]");
      }
    } catch (IOException e) {
      log.error("Failed to initialize ECS mock directories", e);
    }
  }

  @Override
  public RunTaskResponse runTask(RunTaskRequest runTaskRequest)
    throws AwsServiceException, SdkClientException {

    String taskId = "task-" + taskCounter.incrementAndGet();
    String taskArn = generateTaskArn(runTaskRequest.cluster(), taskId);

    try {
      // Store the request payload
      String requestPayload = JsonUtils.toJson(runTaskRequest);
      Files.writeString(Path.of(TASKS_DIR, taskId + "-request.json"), requestPayload);

      // Create task metadata
      TaskData taskData = TaskData.builder()
        .taskId(taskId)
        .taskArn(taskArn)
        .cluster(runTaskRequest.cluster())
        .taskDefinition(runTaskRequest.taskDefinition())
        .lastStatus("RUNNING")
        .desiredStatus("RUNNING")
        .createdAt(Instant.now().toString())
        .tags(runTaskRequest.tags())
        .assignedIp(generateRandomIp())
        .build();

      // Store task metadata
      String taskMetadata = JsonUtils.toJson(taskData);
      Files.writeString(Path.of(TASKS_DIR, taskId + "-metadata.json"), taskMetadata);

      // Add to running tasks list
      addToRunningTasks(taskArn);

      // Create response
      Task task = createTaskFromData(taskData);

      return RunTaskResponse.builder()
        .tasks(task)
        .build();

    } catch (IOException e) {
      throw InternalServiceErrorException.create("Failed to save task metadata", e);
    }
  }

  private String generateRandomIp() {
    Random rand = new Random();
    return String.format("%d.%d.%d.%d",
                         rand.nextInt(256),
                         rand.nextInt(256),
                         rand.nextInt(256),
                         rand.nextInt(256));
  }

  @Override
  public StopTaskResponse stopTask(StopTaskRequest stopTaskRequest)
    throws AwsServiceException, SdkClientException {

    try {
      String taskId = extractTaskIdFromArn(stopTaskRequest.task());
      Path taskMetadataPath = Path.of(TASKS_DIR, taskId + "-metadata.json");

      if (!Files.exists(taskMetadataPath)) {
        throw new RuntimeException("Task not found: " + taskId);
      }

      // Update task status
      String metadataJson = Files.readString(taskMetadataPath);
      TaskData taskData = JsonUtils.parseJson(metadataJson, TaskData.class);
      TaskData updatedTaskData = taskData.toBuilder()
        .lastStatus("STOPPED")
        .desiredStatus("STOPPED")
        .stoppedAt(Instant.now().toString())
        .build();

      Files.writeString(taskMetadataPath, JsonUtils.toJson(updatedTaskData));

      // Remove from running tasks
      removeFromRunningTasks(stopTaskRequest.task());

      Task stoppedTask = createTaskFromData(updatedTaskData);

      return StopTaskResponse.builder()
        .task(stoppedTask)
        .build();

    } catch (IOException e) {
      throw InternalServiceErrorException.create("Failed to stop task", e);
    }
  }

  @Override
  public ListTasksResponse listTasks(ListTasksRequest listTasksRequest)
    throws AwsServiceException, SdkClientException {

    try {
      List<String> runningTaskArns = getRunningTaskArns();

      // Filter by region first - only show tasks from this client's region
      runningTaskArns = runningTaskArns.stream()
        .filter(arn -> isGlobal || arn.contains(":" + region + ":"))
        .collect(Collectors.toList());

      // Filter by cluster if specified
      if (listTasksRequest.cluster() != null) {
        runningTaskArns = runningTaskArns.stream()
          .filter(arn -> arn.contains("/" + listTasksRequest.cluster() + "/"))
          .collect(Collectors.toList());
      }

      return ListTasksResponse.builder()
        .taskArns(runningTaskArns)
        .build();

    } catch (IOException e) {
      throw InternalServiceErrorException.create("Failed to list tasks", e);
    }
  }

  @Override
  public DescribeTasksResponse describeTasks(DescribeTasksRequest describeTasksRequest)
    throws AwsServiceException, SdkClientException {

    try {
      List<Task> tasks = new ArrayList<>();

      for (String taskArn : describeTasksRequest.tasks()) {
        // Only process tasks from this client's region
        if (!isGlobal && !taskArn.contains(":" + region + ":")) {
          continue;
        }

        String taskId = extractTaskIdFromArn(taskArn);
        Path taskMetadataPath = Path.of(TASKS_DIR, taskId + "-metadata.json");

        if (Files.exists(taskMetadataPath)) {
          String metadataJson = Files.readString(taskMetadataPath);
          TaskData taskData = JsonUtils.parseJson(metadataJson, TaskData.class);

          // Double-check that the task's stored region matches this client's region
          if (isGlobal || taskData.getTaskArn().contains(":" + region + ":")) {
            Task task = createTaskFromData(taskData);
            tasks.add(task);
          }
        }
      }

      return DescribeTasksResponse.builder()
        .tasks(tasks)
        .build();

    } catch (IOException e) {
      throw InternalServiceErrorException.create("Failed to describe tasks", e);
    }
  }

  private Task createTaskFromData(TaskData taskData) {
    // Create network interface attachment
    List<KeyValuePair> attachmentDetails = List.of(
      KeyValuePair.builder()
        .name("networkInterfaceId")
        .value("eni-" + taskData.getTaskId())
        .build()
    );

    Attachment networkAttachment = Attachment.builder()
      .type("ElasticNetworkInterface")
      .status("ATTACHED")
      .details(attachmentDetails)
      .build();

    // Convert tags
    List<Tag> tags = taskData.getTags().stream()
      .map(tag -> Tag.builder().key(tag.key()).value(tag.value()).build())
      .collect(Collectors.toList());

    return Task.builder()
      .taskArn(taskData.getTaskArn())
      .taskDefinitionArn(taskData.getTaskDefinition())
      .clusterArn(taskData.getCluster())
      .lastStatus(taskData.getLastStatus())
      .desiredStatus(taskData.getDesiredStatus())
      .createdAt(Instant.parse(taskData.getCreatedAt()))
      .stoppedAt(taskData.getStoppedAt() != null ? Instant.parse(taskData.getStoppedAt()) : null)
      .tags(tags)
      .attachments(networkAttachment)
      .build();
  }

  private String generateTaskArn(String cluster, String taskId) {
    return String.format("arn:aws:ecs:%s:123456789012:task/%s/%s", region, cluster, taskId);
  }

  private String extractTaskIdFromArn(String taskArn) {
    return taskArn.substring(taskArn.lastIndexOf('/') + 1);
  }

  private void addToRunningTasks(String taskArn) throws IOException {
    List<String> runningTasks = getRunningTaskArns();
    if (!runningTasks.contains(taskArn)) {
      runningTasks.add(taskArn);
      Files.writeString(Path.of(RUNNING_TASKS_FILE), JsonUtils.toJson(runningTasks));
    }
  }

  private void removeFromRunningTasks(String taskArn) throws IOException {
    List<String> runningTasks = getRunningTaskArns();
    runningTasks.remove(taskArn);
    Files.writeString(Path.of(RUNNING_TASKS_FILE), JsonUtils.toJson(runningTasks));
  }

  @SuppressWarnings("unchecked")
  private List<String> getRunningTaskArns() throws IOException {
    String runningTasksJson = Files.readString(Path.of(RUNNING_TASKS_FILE));
    return JsonUtils.parseJson(runningTasksJson, new TypeReference<>() {
    });
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    try {
      FileUtils.deleteDirectory(new File(ROOT_DIR));
      log.info("Cleaned up ECS mock test directory");
    } catch (IOException e) {
      log.error("Failed to clean up test directory", e);
    }
  }
}
