package org.github.akarkin1.waiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.ec2.WaitParameters;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;

import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public class InstanceStateWaiter {
  private final Ec2Client ec2Client;
  private final WaitParameters waitParameters;

  public Optional<Instance> waitForStatus(String instanceId, InstanceStateName desiredState) {
    int attempt = 1;
    long started = System.currentTimeMillis();
    log.debug("Start waiting for state: {}", desiredState.name());

    for(;;) {
      DescribeInstancesRequest request = DescribeInstancesRequest.builder()
          .instanceIds(instanceId)
          .build();

      log.debug("Check for current instance state: attempt: {}", attempt);
      DescribeInstancesResponse response = ec2Client.describeInstances(request);
      if (!response.hasReservations()) {
        failWithNotFoundError(instanceId, desiredState);
      }

      Optional<Instance> instanceOrEmpty = response.reservations().stream()
          .flatMap(reservation -> reservation.instances().stream())
          .filter(instance -> instance.instanceId().equals(instanceId))
          .findFirst();

      if (instanceOrEmpty.isEmpty()) {
        failWithNotFoundError(instanceId, desiredState);
      }

      Instance latestInstance = instanceOrEmpty.get();
      InstanceStateName stateName = latestInstance.state().name();
      log.debug("Current state: {}", stateName);
      if (desiredState == stateName) {
        log.debug("Current state matches the desired state. Waiting is stopped");
        return Optional.of(latestInstance);
      } else {
        long waitTime = waitParameters.getStatusWaitStrategy().getWaitTime(attempt++);
        log.debug("Current state doesn't match the desire state. Sleep for {}ms", waitTime);
        sleepQuietly(waitTime);
        long totalTimeWaited = System.currentTimeMillis() - started;
        if (totalTimeWaited > waitParameters.getOperationTimeout()) {
          log.debug("Wait timeout!");
          return Optional.empty();
        }
      }
    }
  }

  private static void failWithNotFoundError(String instanceId, InstanceStateName desiredState) {

    String errorMessage = "Failed to check desired state: %s for the instance with ID: %s. "
        + "Resource not found"
        .formatted(desiredState.toString(), instanceId);
    throw new CommandExecutionFailedException(errorMessage);
  }

  private static void sleepQuietly(long waitTime) {
    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {
      log.warn("Sleep failed", e);
    }
  }


}
