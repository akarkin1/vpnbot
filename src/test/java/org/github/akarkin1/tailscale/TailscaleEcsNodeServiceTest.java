package org.github.akarkin1.tailscale;

import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TailscaleEcsNodeServiceTest {

  private static final String REGION = "eu-central-1";
  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final Map<String, String> ENV = Map.of("TG_CHAT_ID", "100");
  private static final EcsNode TASK = EcsNode.builder().id(TASK_ID).build();

  @Mock
  private EcsManager ecsManager;
  @Captor
  private ArgumentCaptor<Map<String, String>> sentTags;

  private TailscaleEcsNodeService service;

  @BeforeEach
  void setUp() {
    EcsConfiguration ecsConfig = new EcsConfiguration();
    ecsConfig.setServiceName("tailscale-node");
    ecsConfig.setServiceNameTag("ServiceName");
    ecsConfig.setHostNameTag("Hostname");
    ecsConfig.setRunByTag("RunBy");
    ecsConfig.setRunByNameTag("RunByName");
    ecsConfig.setChatIdTag("ChatId");
    ecsConfig.setLanguageTag("Lang");
    AWSConfiguration awsConfig = new AWSConfiguration();
    awsConfig.setRegionCities(Map.of(REGION, "Frankfurt"));
    service = new TailscaleEcsNodeService(ecsManager, ecsConfig, awsConfig);
  }

  @Test
  @DisplayName("2b AC-9, 4 AC-9: runNode tags the task with RunBy = user id, RunByName = username, ChatId and Lang of the NodeOrigin")
  void runNodeTagsOwner() {
    Map<String, String> tags = Map.of("Hostname", "alex-frankfurt-1",
                                      "RunBy", "7",
                                      "RunByName", "alex",
                                      "ServiceName", "tailscale-node",
                                      "ChatId", "100",
                                      "Lang", "ru");
    when(ecsManager.startTask(eq(Region.EU_CENTRAL_1), eq("alex-frankfurt-1"), any(), eq(ENV)))
        .thenReturn(TASK);

    EcsNode started = service.runNode(REGION, new NodeOrigin(7L, "alex", 100L, "ru"),
                                       "alex-frankfurt-1", ENV);

    assertSame(TASK, started);
    verify(ecsManager).startTask(eq(Region.EU_CENTRAL_1), eq("alex-frankfurt-1"), sentTags.capture(),
                                 eq(ENV));
    assertEquals(tags, sentTags.getValue());
  }

  @Test
  @DisplayName("4 AC-9: an owner without a username gets RunBy = user id and no RunByName tag")
  void runNodeWithoutUsername() {
    when(ecsManager.startTask(eq(Region.EU_CENTRAL_1), eq("node-11"), any(), eq(ENV)))
        .thenReturn(TASK);

    service.runNode(REGION, new NodeOrigin(11L, null, 100L, "en"), "node-11", ENV);

    verify(ecsManager).startTask(eq(Region.EU_CENTRAL_1), eq("node-11"), sentTags.capture(), eq(ENV));
    assertEquals("11", sentTags.getValue().get("RunBy"));
    assertFalse(sentTags.getValue().containsKey("RunByName"), sentTags.getValue().toString());
  }

  @Test
  @DisplayName("4 §2 (no change to node start): a generated host name is still built from the username")
  void generatedHostNameFromUsername() {
    when(ecsManager.listTasks(Map.of("ServiceName", "tailscale-node"))).thenReturn(List.of());
    when(ecsManager.startTask(eq(Region.EU_CENTRAL_1), eq("alex-frankfurt-1"), any(), eq(ENV)))
        .thenReturn(TASK);

    service.runNode(REGION, new NodeOrigin(7L, "alex", 100L, "ru"), null, ENV);

    verify(ecsManager).startTask(eq(Region.EU_CENTRAL_1), eq("alex-frankfurt-1"), sentTags.capture(),
                                 eq(ENV));
    assertEquals("7", sentTags.getValue().get("RunBy"));
  }

  @Test
  @DisplayName("4 §4.1: listNodes(userId) matches RunBy against the given user id")
  void listNodesByUserId() {
    List<EcsNode> tasks = List.of(TASK);
    when(ecsManager.listTasks(Map.of("ServiceName", "tailscale-node", "RunBy", "7")))
        .thenReturn(tasks);

    assertSame(tasks, service.listNodes("7"));
  }

  @Test
  @DisplayName("4 §4.1: listNodes(null) lists every node of the service (root)")
  void listNodesOfAll() {
    List<EcsNode> tasks = List.of(TASK);
    when(ecsManager.listTasks(Map.of("ServiceName", "tailscale-node"))).thenReturn(tasks);

    assertSame(tasks, service.listNodes(null));
  }

  @Test
  @DisplayName("2b AC-9: getNode reads the task from ECS in the node's region")
  void getNodeDelegates() {
    when(ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID)).thenReturn(Optional.of(TASK));

    assertEquals(Optional.of(TASK), service.getNode(REGION, TASK_ID));
  }

  @Test
  @DisplayName("2b AC-9: getNode is empty when ECS does not find a running task")
  void getNodeEmpty() {
    when(ecsManager.getTask(Region.EU_CENTRAL_1, TASK_ID)).thenReturn(Optional.empty());

    assertEquals(Optional.empty(), service.getNode(REGION, TASK_ID));
  }

  @Test
  @DisplayName("2b AC-9: stopNode stops the task in the node's region with the reason")
  void stopNodeDelegates() {
    service.stopNode(REGION, TASK_ID, "Stopped by @alex via the bot");

    verify(ecsManager).stopTask(Region.EU_CENTRAL_1, TASK_ID, "Stopped by @alex via the bot");
  }

}
