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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.HealthStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

  @Mock
  private TaskConfigService taskConfigService;
  @Mock
  private EcsClientPool ecsClientPool;
  @Mock
  private Ec2ClientPool ec2ClientPool;
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
    config.setHostNameEnv("TAILSCALE_HOSTNAME");

    when(taskConfigService.getSupportedRegions()).thenReturn(REGIONS);
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
