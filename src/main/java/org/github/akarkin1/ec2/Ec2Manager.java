package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.waiter.InstanceStateWaiter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Log4j2
@RequiredArgsConstructor
public class Ec2Manager {

  private static final String SVC_NAME_TAG = "tag:ServiceName";
  private static final String NAME_TAG = "Name";
  private static final String SERVICE_NAME_VAL = "openvpn-server";
  private static final String LOCATION_TAG = "Location";
  private static final String SERVER_NAME_TAG = "Name";
  private static final long STATUS_CHECK_PAUSE_MS = 500;
  private static final long WAIT_TIMEOUT_MS = 60_000;

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
    callStopInstancesInTheRegion(regionInstance);
  }

  private void callStopInstancesInTheRegion(RegionInstance regionInstance) {
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
    callStartInstancesInTheRegion(regionInstance);
  }

  private void callStartInstancesInTheRegion(RegionInstance regionInstance) {
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

  public void startServer(String serverName) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s".formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    callStartInstancesInTheRegion(regionInstance);
  }

  public void startServerGracefully(String serverName, Consumer<String> messageConsumer) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s".formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    messageConsumer.accept("Starting the server %s ...".formatted(serverName));
    callStartInstancesInTheRegion(regionInstance);

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());
    Instance instance = regionInstance.instance();
    String instanceId = instance.instanceId();
    boolean isSucceed = waitForTheState(ec2, instanceId, InstanceStateName.RUNNING);
    if (isSucceed) {
      messageConsumer.accept("Server %s has started successfully".formatted(serverName));
      messageConsumer.accept(" - Instance ID: %s\n - Public IP:%s".formatted(
          instanceId,
          instance.publicIpAddress()));
    } else {
      messageConsumer.accept(("Failed to check instance state. Timeout %ds has exceeded."
          + "Try to run /servers command later to ensure the server has started.")
                                 .formatted(getWaitTimeoutSeconds()));
    }
  }

  public void stopServerGracefully(String serverName, Consumer<String> messageConsumer) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s".formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    messageConsumer.accept("Stopping the instance ...");
    callStopInstancesInTheRegion(regionInstance);

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());
    Instance instance = regionInstance.instance();
    String instanceId = instance.instanceId();
    boolean isSucceed = waitForTheState(ec2, instanceId,
                                        InstanceStateName.STOPPED);
    if (isSucceed) {
      messageConsumer.accept("Server %s has stopped successfully".formatted(serverName));
    } else {
      messageConsumer.accept(("Failed to check instance state. Timeout %ds has exceeded."
          + "Try to run /servers command later to ensure the server has started.")
                                 .formatted(getWaitTimeoutSeconds()));
    }
  }

  public void restartServerGracefully(String serverName, Consumer<String> messageConsumer) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s"
                                                    .formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    messageConsumer.accept("Stopping the instance ...");
    callStopInstancesInTheRegion(regionInstance);

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());
    Instance instance = regionInstance.instance();
    String instanceId = instance.instanceId();
    boolean isSucceed = waitForTheState(ec2, instanceId, InstanceStateName.STOPPED);
    if (isSucceed) {
      messageConsumer.accept("Server %s has stopped successfully".formatted(serverName));
    } else {
      messageConsumer.accept(("Failed to check instance state. Timeout %ds has exceeded."
          + "Try to run /servers command later to ensure the server has started.")
                                 .formatted(getWaitTimeoutSeconds()));
    }

    messageConsumer.accept("Starting the instance ...");
    callStartInstancesInTheRegion(regionInstance);

    isSucceed = waitForTheState(ec2, instanceId, InstanceStateName.RUNNING);
    if (isSucceed) {
      messageConsumer.accept("Server %s has started successfully".formatted(serverName));
      messageConsumer.accept(" - Instance ID: %s%n - Public IP:%s".formatted(
          instanceId,
          instance.publicIpAddress()));
    } else {
      messageConsumer.accept(("Failed to check instance state. Timeout %ds has exceeded."
          + "Try to run /servers command later to ensure the server has started.")
                                 .formatted(getWaitTimeoutSeconds()));
    }
  }

  private static long getWaitTimeoutSeconds() {
    return TimeUnit.MILLISECONDS.toSeconds(WAIT_TIMEOUT_MS);
  }


  public void rebootServer(String serverName) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s".formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    callRebootInstancesInTheRegion(regionInstance);
  }

  private void callRebootInstancesInTheRegion(RegionInstance regionInstance) {
    InstanceState state = regionInstance.instance().state();
    InstanceStateName stateName = state.name();

    Ec2Client ec2 = clientProvider.getForRegion(regionInstance.region().id());
    String instanceId = regionInstance.instance().instanceId();

    if (stateName == InstanceStateName.STOPPED) {
      StartInstancesRequest request = StartInstancesRequest.builder()
          .instanceIds(instanceId).build();
      ec2.startInstances(request);
    } else if (stateName == InstanceStateName.RUNNING) {
      RebootInstancesRequest rebootInstancesRequest = RebootInstancesRequest.builder()
          .instanceIds(instanceId)
          .build();

      ec2.rebootInstances(rebootInstancesRequest);
    } else {
      throw new CommandExecutionFailedException("Invalid server state: %s".formatted(state.name()));
    }
  }

  private static Predicate<Instance> serverNameMatches(String serverName) {
    return instance -> instance.tags()
        .stream()
        .filter(tag -> tag.key().equals(SERVER_NAME_TAG))
        .filter(tag -> tag.value().equals(serverName))
        .count() == 1;
  }

  public void stopServer(String serverName) {
    Optional<RegionInstance> regionInstanceOrEmpty = locateInstance(serverNameMatches(serverName));

    if (regionInstanceOrEmpty.isEmpty()) {
      throw new CommandExecutionFailedException("No instance found with ServerName: %s".formatted(serverName));
    }

    RegionInstance regionInstance = regionInstanceOrEmpty.get();
    callStopInstancesInTheRegion(regionInstance);
  }

  private boolean waitForTheState(Ec2Client ec2, String instanceId, InstanceStateName state) {
    WaitParameters waitParams = WaitParameters.builder()
        .statusWaitStrategy(FixedDelayWaitStrategy.create(STATUS_CHECK_PAUSE_MS))
        .operationTimeout(getWaitTimeoutSeconds())
        .build();
    InstanceStateWaiter waiter = new InstanceStateWaiter(ec2, waitParams);
    return waiter.waitForStatus(instanceId, state);
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
