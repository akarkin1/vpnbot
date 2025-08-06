package org.github.akarkin1.ecs;

import org.github.akarkin1.ec2.Ec2ClientProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.NetworkInterface;
import software.amazon.awssdk.services.ec2.model.NetworkInterfaceAssociation;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;
import software.amazon.awssdk.services.ecs.model.StopTaskResponse;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EcsTaskTestHarness {

    public static final List<String> AVAILABLE_REGIONS = List.of("us-east-1", "eu-central-1",
                                                                 "us-west-2", "ap-southeast-1");
    public static final int TASK_EXPIRATION_TIME_SEC = 30;
    private final MockEcsClientProvider ecsClientProvider;
    private final Ec2ClientProvider ec2ClientProvider;
    private final Map<String, TaskMetadata> runningTasks = new ConcurrentHashMap<>();
    private int taskCounter = 0;

    public EcsTaskTestHarness(MockEcsClientProvider ecsClientProvider, Ec2ClientProvider ec2ClientProvider) {
        this.ecsClientProvider = ecsClientProvider;
        this.ec2ClientProvider = ec2ClientProvider;
    }

    public void setupEcsClientMocks() {
        // Mock the default client
        setupClientMocks(ecsClientProvider.getDefaultClient());

        // Pre-allocate clients for each region, so they will be configured later
        AVAILABLE_REGIONS.stream().map(Region::of).forEach(ecsClientProvider::get);

        // Mock clients for all regions
        ecsClientProvider.getAllClients().values().forEach(this::setupClientMocks);
    }

    public void setupEc2ClientMocks() {
        // Mock the global client
        setupEc2ClientMocks(ec2ClientProvider.getForGlobal());

        // Mock clients for all regions that might be used
        for (String regionName : AVAILABLE_REGIONS) {
            Ec2Client ec2Client = ec2ClientProvider.getForRegion(regionName);
            setupEc2ClientMocks(ec2Client);
        }
    }

    private void setupEc2ClientMocks(Ec2Client ec2Client) {
        // Mock describeNetworkInterfaces to return a network interface with a public IP
        when(ec2Client.describeNetworkInterfaces(any(DescribeNetworkInterfacesRequest.class)))
            .thenAnswer(invocation -> {
                DescribeNetworkInterfacesRequest request = invocation.getArgument(0);

                // Create a mock network interface association with public IP
                NetworkInterfaceAssociation association = NetworkInterfaceAssociation.builder()
                    .publicIp("203.0.113.42")  // Mock public IP (RFC 5737 test range)
                    .associationId("eipassoc-12345678")
                    .allocationId("eipalloc-12345678")
                    .build();

                // Create a mock network interface
                NetworkInterface networkInterface = NetworkInterface.builder()
                    .networkInterfaceId(request.networkInterfaceIds().get(0))
                    .association(association)
                    .status("in-use")
                    .privateIpAddress("10.0.1.100")
                    .build();

                return DescribeNetworkInterfacesResponse.builder()
                    .networkInterfaces(networkInterface)
                    .build();
            });
    }

    private void setupClientMocks(EcsClient client) {
        // Mock runTask
        when(client.runTask(any(RunTaskRequest.class))).thenAnswer(invocation -> {
            RunTaskRequest request = invocation.getArgument(0);
            String taskId = "task-" + (++taskCounter);
            String cluster = request.cluster();

            // Create a running task using builder pattern
            TaskInfo taskInfo = TaskInfo.builder()
                .id(taskId)
                .cluster(cluster)
                .region(Region.US_EAST_1)
                .state(RunTaskStatus.HEALTHY.name())
                .hostName("test-hostname")
                .serviceName("vpn")
                .publicIp("203.0.113.42")
                .location("us-east-1")
                .build();

            runningTasks.put(taskId, new TaskMetadata(taskInfo, System.currentTimeMillis()));

            // Create proper tags from the request
            List<Tag> tags = request.tags().stream()
                .map(tag -> Tag.builder().key(tag.key()).value(tag.value()).build())
                .collect(java.util.stream.Collectors.toList());

            // Create task attachments with network interface details
            List<KeyValuePair> attachmentDetails = List.of(
                KeyValuePair.builder()
                    .name("networkInterfaceId")
                    .value("eni-" + taskId)
                    .build()
            );

            Attachment networkAttachment = Attachment.builder()
                .type("ElasticNetworkInterface")
                .status("ATTACHED")
                .details(attachmentDetails)
                .build();

            Task task = Task.builder()
                .taskArn("arn:aws:ecs:us-east-1:123456789012:task/" + cluster + "/" + taskId)
                .taskDefinitionArn(request.taskDefinition())
                .clusterArn(cluster)
                .lastStatus("RUNNING")
                .desiredStatus("RUNNING")
                .tags(tags)  // Include the tags from the request
                .attachments(networkAttachment)  // Include network interface attachment
                .build();

            return RunTaskResponse.builder()
                .tasks(task)
                .build();
        });

        // Mock listTasks
        when(client.listTasks(any(ListTasksRequest.class))).thenAnswer(invocation -> {
            pruneExpiredTasks();

            List<String> taskArns = runningTasks.values().stream()
                .map(TaskMetadata::taskInfo)
                .map(task -> "arn:aws:ecs:us-east-1:123456789012:task/" + task.getCluster() + "/" + task.getId())
                .collect(Collectors.toCollection(ArrayList::new));

            return ListTasksResponse.builder()
                .taskArns(taskArns)
                .build();
        });

        // Mock describeTasks - this is crucial for the NPE fix
        when(client.describeTasks(any(DescribeTasksRequest.class))).thenAnswer(invocation -> {
            DescribeTasksRequest request = invocation.getArgument(0);
            pruneExpiredTasks();

            List<Task> tasks = request.tasks().stream()
                .map(taskArn -> {
                    String taskId = extractTaskIdFromArn(taskArn);
                    TaskMetadata taskMetadata = runningTasks.get(taskId);
                    if (taskMetadata == null) {
                        return null;
                    }

                    TaskInfo taskInfo = taskMetadata.taskInfo();
                    if (taskInfo != null) {
                        // Create tags that match what EcsManagerImpl expects
                        List<Tag> tags = List.of(
                            Tag.builder().key("HostName").value(taskInfo.getHostName()).build(),
                            Tag.builder().key("ServiceName").value(taskInfo.getServiceName()).build(),
                            Tag.builder().key("RunBy").value("test-user").build()
                        );

                        // Create task attachments with network interface details
                        List<KeyValuePair> attachmentDetails = List.of(
                            KeyValuePair.builder()
                                .name("networkInterfaceId")
                                .value("eni-" + taskId)
                                .build()
                        );

                        Attachment networkAttachment = Attachment.builder()
                            .type("ElasticNetworkInterface")
                            .status("ATTACHED")
                            .details(attachmentDetails)
                            .build();

                        return Task.builder()
                            .taskArn(taskArn)
                            .clusterArn(taskInfo.getCluster())
                            .lastStatus(taskInfo.getState())
                            .desiredStatus("RUNNING")
                            .tags(tags)  // This prevents the NPE in EcsManagerImpl
                            .attachments(networkAttachment)  // Include network interface for IP lookup
                            .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

            return DescribeTasksResponse.builder()
                .tasks(tasks)
                .build();
        });

        // Mock stopTask
        when(client.stopTask(any(StopTaskRequest.class))).thenAnswer(invocation -> {
            StopTaskRequest request = invocation.getArgument(0);
            String taskId = extractTaskIdFromArn(request.task());
            runningTasks.remove(taskId);

            return StopTaskResponse.builder()
                .task(Task.builder()
                    .taskArn(request.task())
                    .clusterArn(request.cluster())
                    .lastStatus("STOPPED")
                    .desiredStatus("STOPPED")
                    .build())
                .build();
        });
    }

    private void pruneExpiredTasks() {
        List<String> taskIdsToRemove = new ArrayList<>();

        runningTasks.forEach((taskId, taskMetadata) -> {
              if (hasTaskExpired(taskMetadata)){
                  taskIdsToRemove.add(taskId);
              }
          });

        if (!taskIdsToRemove.isEmpty()) {
            taskIdsToRemove.forEach(runningTasks::remove);
        }
    }

    private static boolean hasTaskExpired(TaskMetadata taskMetadata) {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - taskMetadata.lastRunAt) >= TASK_EXPIRATION_TIME_SEC;
    }

    private String extractTaskIdFromArn(String taskArn) {
        return taskArn.substring(taskArn.lastIndexOf('/') + 1);
    }

    private record TaskMetadata(TaskInfo taskInfo, long lastRunAt) {}
}
