package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.exception.InstanceNotFoundException;
import org.github.akarkin1.waiter.InstanceStateWaiter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateChange;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Log4j2
@RequiredArgsConstructor
public class Ec2InstanceManager {

  private static final String SVC_NAME_TAG = "tag:ServiceName";
  private static final String NAME_TAG = "Name";
  private static final String SERVICE_NAME_VAL = "openvpn-server";
  private static final String LOCATION_TAG = "Location";
  private static final long STATUS_CHECK_PAUSE_MS = 400;
  private static final long WAIT_TIMEOUT_MS = 10_000;

  private final Ec2ClientProvider clientProvider;


  public static List<Region> getSupportedRegions() {
    return List.of(Region.US_EAST_1, Region.EU_WEST_2, Region.EU_NORTH_1);
  }

// ToDo: This way is too costly
//  public List<Region> getAllRegions() {
//    log.debug("Sending Describe Regions request to AWS...");
//    Ec2Client ec2 = clientProvider.getForGlobal();
//    DescribeRegionsResponse resp = ec2.describeRegions();
//    log.debug("describeRegionsResponse={}", resp);
//    return resp.regions();
//  }

  public List<InstanceInfo> getAllInstances() {
    List<InstanceInfo> instanceInfos = new ArrayList<>();

    for (Region region : getSupportedRegions()) {
      Ec2Client ec2 = clientProvider.getForRegion(region.id());

      // It's even more interesting without the filter.
//      Filter svcNameFilter = Filter.builder().name(SVC_NAME_TAG)
//          .values(SERVICE_NAME_VAL)
//          .build();
      DescribeInstancesRequest request = DescribeInstancesRequest.builder()
//          .filters(svcNameFilter)
          .build();

      log.debug("Sending Describe Instances request to AWS...");
      DescribeInstancesResponse response = ec2.describeInstances(request);
      log.debug("describeInstancesResponse={}", response);

      if (!response.hasReservations()) {
        return instanceInfos;
      }

      for (Reservation reservation : response.reservations()) {
        if (!reservation.hasInstances()) {
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
                                .region(region.id())
                                .publicIp(publicIp)
                                .build());
        }

      }
    }

    return instanceInfos;
  }

  public void stopInstance(String instanceId) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(
        instance -> instance.instanceId().equals(instanceId));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ID: %s".formatted(instanceId));
    }
    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    InstanceState state = regionInstance.instance().state();
    InstanceStateName stateName = state.name();

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());

    if (stateName == InstanceStateName.STOPPING) {
      return;
    }

    StopInstancesRequest request = StopInstancesRequest.builder()
        .instanceIds(regionInstance.instance().instanceId()).build();
    ec2.stopInstances(request);
  }

  public void startInstance(String instanceId) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(
        instance -> instance.instanceId().equals(instanceId));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ID: %s".formatted(instanceId));
    }
    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    InstanceState state = regionInstance.instance().state();
    InstanceStateName stateName = state.name();

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());

    if (stateName == InstanceStateName.RUNNING) {
      return;
    }

    StartInstancesRequest request = StartInstancesRequest.builder()
        .instanceIds(regionInstance.instance().instanceId()).build();
    ec2.startInstances(request);
  }

  public StopResult stopInstanceGracefully(String instanceId) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(
        instance -> instance.instanceId().equals(instanceId));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ID: %s".formatted(instanceId));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    InstanceState state = regionInstance.instance().state();
    InstanceStateName stateName = state.name();

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());

    if (stateName == InstanceStateName.STOPPING) {
      return waitUntilStopped(ec2, instanceId);
    } else if(stateName == InstanceStateName.STOPPED
        || stateName == InstanceStateName.SHUTTING_DOWN
        || stateName == InstanceStateName.TERMINATED) {

      return StopResult.ALREADY_STOPPED;
    }

    StopInstancesRequest request = StopInstancesRequest.builder()
        .instanceIds(regionInstance.instance().instanceId()).build();
    StopInstancesResponse response = ec2.stopInstances(request);
    Optional<InstanceState> currentInstState = response.stoppingInstances().stream()
        .map(InstanceStateChange::currentState).findFirst();

    if (currentInstState.isEmpty()) {
      return StopResult.UNKNOWN;
    } else {
      return waitUntilStopped(ec2, instanceId);
    }
  }

  private StopResult waitUntilStopped(Ec2Client ec2, String instanceId) {
    WaitParameters waitParams = WaitParameters.builder()
        .statusWaitStrategy(FixedDelayWaitStrategy.create(STATUS_CHECK_PAUSE_MS))
        .operationTimeout(WAIT_TIMEOUT_MS)
        .build();
    InstanceStateWaiter waiter = new InstanceStateWaiter(ec2, waitParams);
    try {
      boolean succeed = waiter.waitForStatus(instanceId, InstanceStateName.STOPPED);

      if (succeed) {
        return StopResult.STOP_SUCCEED;
      } else {
        return StopResult.STOP_WAIT_TIMEOUT;
      }
    } catch (InstanceNotFoundException e) {
      return StopResult.INSTANCE_NOT_FOUND;
    }
  }

  private static void sleepQuietly(long waitTime) {
    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {
      log.warn("Sleep failed", e);
    }
  }

  private Optional<RegionInstance> locateInstance(Predicate<Instance> instancePredicate) {
    for (Region supportedRegion : getSupportedRegions()) {
      Ec2Client ec2 = clientProvider.getForRegion(supportedRegion.id());
      DescribeInstancesResponse response = ec2.describeInstances();
      if (!response.hasReservations()) {
        continue;
      }

      Optional<Instance> instance = response.reservations().stream()
          .flatMap(reservation -> reservation.instances().stream())
          .filter(instancePredicate)
          .findFirst();

      if (instance.isPresent()) {
        return Optional.of(new RegionInstance(supportedRegion, instance.get()));
      }
    }

    return Optional.empty();
  }

}
