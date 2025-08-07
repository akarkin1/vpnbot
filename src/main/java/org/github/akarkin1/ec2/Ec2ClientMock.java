package org.github.akarkin1.ec2;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.ecs.TaskData;
import org.github.akarkin1.util.JsonUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.NetworkInterface;
import software.amazon.awssdk.services.ec2.model.NetworkInterfaceAssociation;
import software.amazon.awssdk.services.ec2.model.NetworkInterfaceStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class Ec2ClientMock implements Ec2Client {

  private static final String DEFAULT_REGION = "us-east-1";
  private static final String TASKS_DIR = ".test-data/ecs/tasks/";

  private final String region;
  private final boolean isGlobal;

  public Ec2ClientMock() {
    this.region = DEFAULT_REGION;
    this.isGlobal = true;
  }

  public Ec2ClientMock(String region) {
    this.region = region;
    this.isGlobal = false;
  }

  @Override
  public DescribeNetworkInterfacesResponse describeNetworkInterfaces(DescribeNetworkInterfacesRequest request)
    throws AwsServiceException, SdkClientException {

    List<NetworkInterface> networkInterfaces = new ArrayList<>();

    for (String networkInterfaceId : request.networkInterfaceIds()) {
      String publicIp = findPublicIpForNetworkInterface(networkInterfaceId);

      if (publicIp != null) {
        // Create network interface association with the found public IP
        NetworkInterfaceAssociation association = NetworkInterfaceAssociation.builder()
          .publicIp(publicIp)
          .associationId("eipassoc-" + networkInterfaceId.substring(4)) // Remove "eni-" prefix
          .allocationId("eipalloc-" + networkInterfaceId.substring(4))
          .build();

        // Create the network interface
        NetworkInterface networkInterface = NetworkInterface.builder()
          .networkInterfaceId(networkInterfaceId)
          .association(association)
          .status(NetworkInterfaceStatus.IN_USE)
          .privateIpAddress("10.0.1." + (Math.abs(networkInterfaceId.hashCode()) % 254 + 1))
          .build();

        networkInterfaces.add(networkInterface);
      }
    }

    return DescribeNetworkInterfacesResponse.builder()
      .networkInterfaces(networkInterfaces)
      .build();
  }

  private String findPublicIpForNetworkInterface(String networkInterfaceId) {
    try {
      // Extract task ID from network interface ID (format: "eni-X")
      if (!networkInterfaceId.startsWith("eni-")) {
        return null;
      }

      String taskId = networkInterfaceId.substring(4); // Remove "eni-" prefix
      Path taskMetadataPath = Path.of(TASKS_DIR, taskId + "-metadata.json");

      if (!Files.exists(taskMetadataPath)) {
        return null;
      }

      String metadataJson = Files.readString(taskMetadataPath);
      TaskData taskData = JsonUtils.parseJson(metadataJson, TaskData.class);

      // Only return IP if the task belongs to the current region
      if (isGlobal || taskData.getRegion().equals(region)) {
        return taskData.getAssignedIp();
      }

      return null;

    } catch (IOException e) {
      log.error("Failed to read task metadata for network interface: {}", networkInterfaceId, e);
      return null;
    }
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    // No resources to clean up
  }
}
