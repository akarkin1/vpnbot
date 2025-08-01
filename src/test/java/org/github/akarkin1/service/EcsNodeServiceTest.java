package org.github.akarkin1.service;

import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.config.YamlApplicationConfiguration.ServiceConfig;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcsNodeServiceTest {

    @Mock
    private EcsManager ecsManager;

    @Mock
    private EcsConfiguration ecsConfig;

    @Mock
    private AWSConfiguration awsConfig;

    @Mock
    private S3Configuration s3Config;

    @Test
    void testIsRegionValid_whenRegionInKnownRegions_shouldReturnTrue() {
        // Given
        String userRegion = "us-east-1";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));

        EcsNodeService service = createService();

        // When
        boolean result = service.isRegionValid(userRegion);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRegionValid_whenRegionInCityMapping_shouldReturnTrue() {
        // Given
        String userRegion = "virginia";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));

        EcsNodeService service = createService();

        // When
        boolean result = service.isRegionValid(userRegion);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRegionValid_whenRegionNotFound_shouldReturnFalse() {
        // Given
        String userRegion = "unknown-region";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));

        EcsNodeService service = createService();

        // When
        boolean result = service.isRegionValid(userRegion);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsRegionSupported_shouldReturnTrue() {
        // Given
        String userRegion = "virginia";
        String serviceName = "vpn";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsManager.getSupportedRegions(serviceName)).thenReturn(Set.of("us-east-1"));

        EcsNodeService service = createService();

        // When
        boolean result = service.isRegionSupported(userRegion, serviceName);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsHostnameAvailable_whenHostnameIsBlank_shouldReturnFalse() {
        // Given
        String userRegion = "us-east-1";
        String userHostName = "";
        String serviceName = "vpn";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));

        EcsNodeService service = createService();

        // When
        boolean result = service.isHostnameAvailable(userRegion, userHostName, serviceName);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsHostnameAvailable_whenHostnameNotInUse_shouldReturnTrue() {
        // Given
        String userRegion = "us-east-1";
        String userHostName = "test-host";
        String serviceName = "vpn";
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsConfig.getHostNameTag()).thenReturn("hostname");
        when(ecsConfig.getServiceNameTag()).thenReturn("service");
        when(ecsManager.listTasks(any())).thenReturn(List.of());

        EcsNodeService service = createService();

        // When
        boolean result = service.isHostnameAvailable(userRegion, userHostName, serviceName);

        // Then
        assertTrue(result);
    }

    @Test
    void testRunNode_shouldReturnTaskInfo() {
        // Given
        String userRegion = "virginia";
        String userTgId = "123456";
        String userHostName = "test-host";
        String serviceName = "vpn";
        TaskInfo mockTaskInfo = createTaskInfo();

        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsConfig.getHostNameTag()).thenReturn("hostname");
        when(ecsConfig.getRunByTag()).thenReturn("runby");
        when(ecsConfig.getServiceNameTag()).thenReturn("service");
        when(ecsManager.startTask(any(), eq(userHostName), eq(serviceName), any(),
                                  anyList())).thenReturn(mockTaskInfo);

        EcsNodeService service = createService();

        // When
        TaskInfo result = service.runNode(userRegion, userTgId, userHostName, serviceName,
                                          Collections.emptyList());

        // Then
        assertNotNull(result);
        assertEquals("task-123", result.getId());
        assertEquals("test-host", result.getHostName());
        assertEquals(serviceName, result.getServiceName());
    }

    @Test
    void testGetFullTaskInfo_shouldReturnOptionalTaskInfo() {
        // Given
        Region region = Region.US_EAST_1;
        String clusterName = "test-cluster";
        String taskId = "task-123";
        TaskInfo mockTaskInfo = createTaskInfo();

        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsManager.getFullTaskInfo(region, clusterName, taskId)).thenReturn(Optional.of(mockTaskInfo));

        EcsNodeService service = createService();

        // When
        Optional<TaskInfo> result = service.getFullTaskInfo(region, clusterName, taskId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("task-123", result.get().getId());
    }

    @Test
    void testCheckNodeStatus_shouldReturnRunTaskStatus() {
        // Given
        TaskInfo taskInfo = createTaskInfo();
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsManager.checkTaskHealth(taskInfo.getRegion(), taskInfo.getCluster(), taskInfo.getId()))
            .thenReturn(RunTaskStatus.HEALTHY);

        EcsNodeService service = createService();

        // When
        RunTaskStatus result = service.checkNodeStatus(taskInfo);

        // Then
        assertEquals(RunTaskStatus.HEALTHY, result);
    }

    @Test
    void testListTasks_shouldReturnTaskList() {
        // Given
        String userTgId = "123456";
        TaskInfo mockTaskInfo = createTaskInfo();

        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(ecsConfig.getRunByTag()).thenReturn("runby");
        when(ecsManager.listTasks(any())).thenReturn(List.of(mockTaskInfo));

        EcsNodeService service = createService();

        // When
        List<TaskInfo> result = service.listTasks(userTgId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task-123", result.get(0).getId());
    }

    @Test
    void testGetSupportedServiceTypes_shouldReturnServiceList() {
        // Given
        ServiceConfig vpnConfig = new ServiceConfig();
        ServiceConfig minecraftConfig = new ServiceConfig();
        when(awsConfig.getRegionCities()).thenReturn(Map.of("us-east-1", "virginia"));
        when(s3Config.getServiceConfigs()).thenReturn(Map.of("vpn", vpnConfig, "minecraft", minecraftConfig));

        EcsNodeService service = createService();

        // When
        List<String> result = service.getSupportedServices();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("vpn"));
        assertTrue(result.contains("minecraft"));
    }

    @Test
    void testSanitizeHostName_shouldSanitizeSpecialCharacters() {
        // Given
        String hostName = "Test_Host.Name 123";

        // When
        String result = EcsNodeService.sanitizeHostName(hostName);

        // Then
        assertEquals("testhostname123", result);
    }

    private EcsNodeService createService() {
        return new EcsNodeService(ecsManager, ecsConfig, awsConfig, s3Config);
    }

    private TaskInfo createTaskInfo() {
        return TaskInfo.builder()
            .id("task-123")
            .hostName("test-host")
            .state("RUNNING")
            .cluster("test-cluster")
            .publicIp("1.2.3.4")
            .location("us-east-1a")
            .region(Region.US_EAST_1)
            .serviceName("vpn")
            .build();
    }
}
