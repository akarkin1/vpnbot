package org.github.akarkin1.ecs;

import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.TaskRuntimeParameters;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.NetworkInterface;
import software.amazon.awssdk.services.ec2.model.NetworkInterfaceAssociation;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Failure;
import software.amazon.awssdk.services.ecs.model.HealthStatus;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskField;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcsManagerImplTest {

  private static final List<Region> REGIONS = List.of(Region.EU_CENTRAL_1, Region.US_EAST_1,
                                                      Region.EU_WEST_2);
  private static final Map<String, String> CITIES = Map.of("eu-central-1", "Frankfurt",
                                                           "us-east-1", "N. Virginia",
                                                           "eu-west-2", "London");
  private static final Map<String, String> MATCHING_TAGS = Map.of("Service", "vpn");
  private static final long BARRIER_TIMEOUT_SEC = 5;
  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";

  @Mock
  private TaskConfigService taskConfigService;
  @Mock
  private EcsClientPool ecsClientPool;
  @Mock
  private Ec2ClientPool ec2ClientPool;
  @Mock
  private Ec2Client ec2Client;
  @Mock
  private EcsClient euClient;
  @Mock
  private EcsClient usClient;
  @Mock
  private EcsClient ukClient;

  private final ExecutorService executor = Executors.newFixedThreadPool(8);
  private EcsManagerImpl ecsManager;

  @BeforeEach
  void setUp() {
    EcsConfiguration config = new EcsConfiguration();
    config.setEssentialContainerName("vpn-container");
    config.setHostNameTag("HostName");
    config.setServiceNameTag("Service");
    config.setServiceName("vpn");
    config.setRunByTag("RunBy");
    config.setChatIdTag("ChatId");
    config.setLanguageTag("Lang");
    config.setHostNameEnv("TAILSCALE_HOSTNAME");

    lenient().when(taskConfigService.getSupportedRegions()).thenReturn(REGIONS);
    stubRegion(Region.EU_CENTRAL_1, euClient);
    stubRegion(Region.US_EAST_1, usClient);
    stubRegion(Region.EU_WEST_2, ukClient);

    ecsManager = new EcsManagerImpl(taskConfigService, ecsClientPool, ec2ClientPool, config,
                                    CITIES, executor, new RecordingRequestMetrics());
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  @Timeout(30)
  @DisplayName("2a AC-9: listTasks queries all regions concurrently and returns tasks in region order")
  void queriesRegionsConcurrently() {
    CyclicBarrier allRegionsQuerying = new CyclicBarrier(REGIONS.size());
    // the first region answers last, so completion order differs from region order
    stubListTasks(euClient, allRegionsQuerying, 300, "eu");
    stubListTasks(usClient, allRegionsQuerying, 0, "us");
    stubListTasks(ukClient, allRegionsQuerying, 100, "uk");

    List<TaskInfo> tasks = ecsManager.listTasks(MATCHING_TAGS);

    assertEquals(List.of("task-eu", "task-us", "task-uk"), tasks.stream().map(TaskInfo::getId).toList());
    assertEquals(List.of("host-eu", "host-us", "host-uk"),
                 tasks.stream().map(TaskInfo::getHostName).toList());
    assertEquals(REGIONS, tasks.stream().map(TaskInfo::getRegion).toList());
    assertEquals(List.of("Frankfurt", "N. Virginia", "London"),
                 tasks.stream().map(TaskInfo::getLocation).toList());
    assertEquals(List.of("HEALTHY", "HEALTHY", "HEALTHY"),
                 tasks.stream().map(TaskInfo::getState).toList());
  }

  @Test
  @Timeout(30)
  @DisplayName("2a AC-9: an exception in any region propagates from listTasks")
  void regionFailurePropagates() {
    IllegalStateException failure = new IllegalStateException("region down");
    lenient().when(euClient.listTasks(any(ListTasksRequest.class))).thenReturn(emptyList());
    lenient().when(usClient.listTasks(any(ListTasksRequest.class))).thenThrow(failure);
    lenient().when(ukClient.listTasks(any(ListTasksRequest.class))).thenReturn(emptyList());

    RuntimeException thrown = assertThrows(RuntimeException.class,
                                           () -> ecsManager.listTasks(MATCHING_TAGS));

    assertTrue(hasCause(thrown, failure), "the region failure is not in the cause chain: " + thrown);
  }

  @Test
  @Timeout(30)
  @DisplayName("2a D-8: a task whose ENI has no association is listed with a null public IP")
  void eniWithoutAssociation() {
    stubEuTasksWithEnis();
    stubEni("eni-a", NetworkInterface.builder().networkInterfaceId("eni-a").build());
    stubEni("eni-b", networkInterfaceWithIp("eni-b", "1.2.3.4"));

    List<TaskInfo> tasks = ecsManager.listTasks(MATCHING_TAGS);

    assertEquals(List.of("task-a", "task-b"), tasks.stream().map(TaskInfo::getId).toList());
    assertNull(tasks.get(0).getPublicIp());
    assertEquals("1.2.3.4", tasks.get(1).getPublicIp());
  }

  @Test
  @Timeout(30)
  @DisplayName("2a D-8: an ENI lookup failure (SdkException) gives that task a null public IP, others are unaffected, no exception")
  void eniLookupFailure() {
    stubEuTasksWithEnis();
    when(ec2Client.describeNetworkInterfaces(eniRequest("eni-a")))
        .thenThrow(Ec2Exception.builder().message("InvalidNetworkInterfaceID.NotFound").build());
    stubEni("eni-b", networkInterfaceWithIp("eni-b", "1.2.3.4"));

    List<TaskInfo> tasks = ecsManager.listTasks(MATCHING_TAGS);

    assertEquals(List.of("task-a", "task-b"), tasks.stream().map(TaskInfo::getId).toList());
    assertEquals(List.of("host-a", "host-b"), tasks.stream().map(TaskInfo::getHostName).toList());
    assertNull(tasks.get(0).getPublicIp());
    assertEquals("1.2.3.4", tasks.get(1).getPublicIp());
  }

  @Test
  @DisplayName("2b AC-9: startTask passes the ChatId and Lang tags (next to RunBy) to RunTask")
  void startTaskPassesOwnerTags() {
    Map<String, String> tags = Map.of("HostName", "alex-frankfurt-1", "RunBy", "alex",
                                      "Service", "vpn", "ChatId", "100", "Lang", "ru");
    when(euClient.runTask(any(RunTaskRequest.class))).thenReturn(
        RunTaskResponse.builder().tasks(Task.builder().taskArn(arn(TASK_ID)).build()).build());

    TaskInfo started = ecsManager.startTask(Region.EU_CENTRAL_1, "alex-frankfurt-1", tags, Map.of());

    ArgumentCaptor<RunTaskRequest> request = ArgumentCaptor.forClass(RunTaskRequest.class);
    verify(euClient).runTask(request.capture());
    Map<String, String> sentTags = request.getValue().tags().stream()
        .collect(Collectors.toMap(Tag::key, Tag::value));
    assertEquals(tags, sentTags);
    assertEquals(TASK_ID, started.getId());
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask describes the task with tags in the region's cluster and maps RunBy/ChatId/Lang")
  void getTaskMapsOwnerTags() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder().tasks(runningTask(
            Tag.builder().key("Service").value("vpn").build(),
            Tag.builder().key("HostName").value("alex-frankfurt-1").build(),
            Tag.builder().key("RunBy").value("alex").build(),
            Tag.builder().key("ChatId").value("100").build(),
            Tag.builder().key("Lang").value("ru").build())).build());

    Optional<TaskInfo> task = ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID);

    ArgumentCaptor<DescribeTasksRequest> request = ArgumentCaptor.forClass(DescribeTasksRequest.class);
    verify(euClient).describeTasks(request.capture());
    assertEquals("cluster-eu-central-1", request.getValue().cluster());
    assertEquals(List.of(TASK_ID), request.getValue().tasks());
    assertTrue(request.getValue().include().contains(TaskField.TAGS), "tags not requested");
    assertTrue(task.isPresent(), "task not found");
    assertEquals(TASK_ID, task.get().getId());
    assertEquals("alex-frankfurt-1", task.get().getHostName());
    assertEquals(Region.EU_CENTRAL_1, task.get().getRegion());
    assertEquals("alex", task.get().getRunBy());
    assertEquals("100", task.get().getChatId());
    assertEquals("ru", task.get().getLanguageCode());
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9, AC-5: getTask resolves the task's public IP for the node card")
  void getTaskResolvesPublicIp() {
    Attachment eni = Attachment.builder()
        .type("ElasticNetworkInterface")
        .details(KeyValuePair.builder().name("networkInterfaceId").value("eni-a").build())
        .build();
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(runningTask(Tag.builder().key("RunBy").value("alex").build()).toBuilder()
                       .attachments(eni).build())
            .build());
    when(ec2ClientPool.getForRegion("eu-central-1")).thenReturn(ec2Client);
    stubEni("eni-a", networkInterfaceWithIp("eni-a", "1.2.3.4"));

    Optional<TaskInfo> task = ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID);

    assertEquals("1.2.3.4", task.map(TaskInfo::getPublicIp).orElse(null));
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask of a task without RunBy/ChatId/Lang tags (started before 2b) has nulls")
  void getTaskWithoutOwnerTags() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(runningTask(Tag.builder().key("Service").value("vpn").build()))
            .build());

    Optional<TaskInfo> task = ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID);

    assertTrue(task.isPresent(), "task not found");
    assertNull(task.get().getRunBy());
    assertNull(task.get().getChatId());
    assertNull(task.get().getLanguageCode());
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask of an unknown task is empty")
  void getTaskUnknown() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(List.of())
            .failures(Failure.builder().arn(TASK_ID).reason("MISSING").build())
            .build());

    assertEquals(Optional.empty(), ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID));
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask is empty when the desired status is STOPPED")
  void getTaskStopping() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(runningTask(Tag.builder().key("RunBy").value("alex").build()).toBuilder()
                       .desiredStatus("STOPPED")
                       .build())
            .build());

    assertEquals(Optional.empty(), ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID));
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask is empty when the last status is STOPPED")
  void getTaskStopped() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(runningTask(Tag.builder().key("RunBy").value("alex").build()).toBuilder()
                       .desiredStatus("STOPPED")
                       .lastStatus("STOPPED")
                       .build())
            .build());

    assertEquals(Optional.empty(), ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID));
  }

  @Test
  @Timeout(30)
  @DisplayName("2b AC-9: getTask is empty when only the last status is STOPPED")
  void getTaskLastStatusStopped() {
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder()
            .tasks(runningTask(Tag.builder().key("RunBy").value("alex").build()).toBuilder()
                       .lastStatus("STOPPED")
                       .build())
            .build());

    assertEquals(Optional.empty(), ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID));
  }

  @Test
  @DisplayName("2b AC-9: stopTask calls StopTask with the region's cluster, the task id and the reason")
  void stopTaskCallsStopTask() {
    ecsManager.stopTask(Region.EU_CENTRAL_1, TASK_ID, "Stopped by @alex via the bot");

    ArgumentCaptor<StopTaskRequest> request = ArgumentCaptor.forClass(StopTaskRequest.class);
    verify(euClient).stopTask(request.capture());
    assertEquals("cluster-eu-central-1", request.getValue().cluster());
    assertEquals(TASK_ID, request.getValue().task());
    assertEquals("Stopped by @alex via the bot", request.getValue().reason());
  }

  private static Task runningTask(Tag... tags) {
    return Task.builder()
        .taskArn(arn(TASK_ID))
        .desiredStatus("RUNNING")
        .lastStatus("RUNNING")
        .tags(tags)
        .containers(Container.builder().name("vpn-container").healthStatus(HealthStatus.HEALTHY).build())
        .attachments(List.of())
        .build();
  }

  private static String arn(String taskId) {
    return "arn:aws:ecs:eu-central-1:123:task/cluster-eu-central-1/" + taskId;
  }

  /** eu-central-1 runs task-a (ENI eni-a) and task-b (ENI eni-b); the other regions run nothing. */
  private void stubEuTasksWithEnis() {
    when(euClient.listTasks(any(ListTasksRequest.class))).thenReturn(
        ListTasksResponse.builder()
            .taskArns("arn:aws:ecs:region:123:task/cluster/task-a",
                      "arn:aws:ecs:region:123:task/cluster/task-b")
            .build());
    when(euClient.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder().tasks(taskWithEni("a"), taskWithEni("b")).build());
    when(usClient.listTasks(any(ListTasksRequest.class))).thenReturn(emptyList());
    when(ukClient.listTasks(any(ListTasksRequest.class))).thenReturn(emptyList());
    when(ec2ClientPool.getForRegion("eu-central-1")).thenReturn(ec2Client);
  }

  private void stubEni(String eniId, NetworkInterface networkInterface) {
    when(ec2Client.describeNetworkInterfaces(eniRequest(eniId))).thenReturn(
        DescribeNetworkInterfacesResponse.builder().networkInterfaces(networkInterface).build());
  }

  private static DescribeNetworkInterfacesRequest eniRequest(String eniId) {
    return argThat(request -> request != null && request.networkInterfaceIds().contains(eniId));
  }

  private static NetworkInterface networkInterfaceWithIp(String eniId, String publicIp) {
    return NetworkInterface.builder()
        .networkInterfaceId(eniId)
        .association(NetworkInterfaceAssociation.builder().publicIp(publicIp).build())
        .build();
  }

  private static Task taskWithEni(String suffix) {
    Attachment eni = Attachment.builder()
        .type("ElasticNetworkInterface")
        .details(KeyValuePair.builder().name("networkInterfaceId").value("eni-" + suffix).build())
        .build();
    return task(suffix).toBuilder().attachments(eni).build();
  }

  private void stubRegion(Region region, EcsClient client) {
    lenient().when(ecsClientPool.get(region)).thenReturn(client);
    lenient().when(taskConfigService.getTaskRuntimeParameters(region)).thenReturn(
        TaskRuntimeParameters.builder()
            .ecsClusterName("cluster-" + region.id())
            .ecsTaskDefinition("task-def")
            .subnetId("subnet-1")
            .securityGroupId("sg-1")
            .build());
  }

  /** listTasks of {@code client} waits until every region is being queried, then answers after {@code delayMs}. */
  private static void stubListTasks(EcsClient client, CyclicBarrier barrier, long delayMs,
                                    String suffix) {
    when(client.listTasks(any(ListTasksRequest.class))).thenAnswer(invocation -> {
      try {
        barrier.await(BARRIER_TIMEOUT_SEC, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new IllegalStateException("regions were not queried concurrently", e);
      }
      Thread.sleep(delayMs);
      return ListTasksResponse.builder()
          .taskArns("arn:aws:ecs:region:123:task/cluster/task-" + suffix)
          .build();
    });
    lenient().when(client.describeTasks(any(DescribeTasksRequest.class))).thenReturn(
        DescribeTasksResponse.builder().tasks(task(suffix)).build());
  }

  private static Task task(String suffix) {
    return Task.builder()
        .taskArn("arn:aws:ecs:region:123:task/cluster/task-" + suffix)
        .tags(Tag.builder().key("Service").value("vpn").build(),
              Tag.builder().key("HostName").value("host-" + suffix).build())
        .containers(Container.builder().name("vpn-container").healthStatus(HealthStatus.HEALTHY).build())
        .attachments(List.of())
        .build();
  }

  private static ListTasksResponse emptyList() {
    return ListTasksResponse.builder().taskArns(List.of()).build();
  }

  private static boolean hasCause(Throwable thrown, Throwable expected) {
    for (Throwable current = thrown; current != null; current = current.getCause()) {
      if (current == expected) {
        return true;
      }
    }
    return false;
  }

}
