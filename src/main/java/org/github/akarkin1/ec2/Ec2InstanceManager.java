package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Region;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public class Ec2InstanceManager {

  private static final String SVC_NAME_TAG = "tag:ServiceName";
  private static final String NAME_TAG = "Name";
  private static final String SERVICE_NAME_VAL = "openvpn-server";
  private static final String LOCATION_TAG = "Location";

  private final Ec2Client ec2Client;

  public List<Region> getAllRegions() {
    log.debug("Sending Describe Regions request to AWS...");
    DescribeRegionsResponse resp = ec2Client.describeRegions();
    log.debug("describeRegionsResponse={}", resp);
    return resp.regions();
  }

  public List<InstanceInfo> getInstances() {
    Filter svcNameFilter = Filter.builder().name(SVC_NAME_TAG)
        .values(SERVICE_NAME_VAL)
        .build();
    DescribeInstancesRequest request = DescribeInstancesRequest.builder()
        .filters(svcNameFilter)
        .build();

    log.debug("Sending Describe Instances request to AWS...");
    DescribeInstancesResponse response = ec2Client.describeInstances(request);
    log.debug("describeInstancesResponse={}", response);

    List<InstanceInfo> instanceInfos = new ArrayList<>();
    if (!response.hasReservations()) {
      return instanceInfos;
    }

    for (Reservation reservation : response.reservations()) {
      if(!reservation.hasInstances()) {
        continue;
      }

      for (Instance instance : reservation.instances()) {
        String instanceId = instance.instanceId();
        String serverName = Optional.ofNullable(instance.tags()).orElse(List.of())
            .stream()
            .filter(tag -> NAME_TAG.equals(tag.key()))
            .map(Tag::value)
            .findFirst()
            .orElse(null);
        String location = Optional.ofNullable(instance.tags()).orElse(List.of())
            .stream()
            .filter(tag -> LOCATION_TAG.equals(tag.key()))
            .map(Tag::value)
            .findFirst()
            .orElse(null);
        String stateName = Optional.of(instance.state())
            .map(InstanceState::name)
            .map(InstanceStateName::toString)
            .orElse(null);
        String publicIp = instance.publicIpAddress();

        instanceInfos.add(InstanceInfo.builder()
                              .id(instanceId)
                              .name(serverName)
                              .state(stateName)
                              .location(location)
                              .publicIp(publicIp)
                              .build());
      }

    }

    return instanceInfos;
  }
}
