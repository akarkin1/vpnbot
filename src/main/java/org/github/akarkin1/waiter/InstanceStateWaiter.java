package org.github.akarkin1.waiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.ec2.WaitParameters;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;

import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public class InstanceStateWaiter {
  private final Ec2Client ec2Client;
  private final WaitParameters waitParameters;

  public boolean waitForStatus(String instanceId, InstanceStateName desiredState) {
    int attempt = 1;
    long started = System.currentTimeMillis();

    for(;;) {
      DescribeInstancesRequest request = DescribeInstancesRequest.builder()
          .instanceIds(instanceId)
          .build();

      DescribeInstancesResponse response = ec2Client.describeInstances(request);
      if (!response.hasReservations()) {
        failWithNotFoundError(instanceId, desiredState);
      }

      Optional<InstanceState> foundState = response.reservations().stream()
          .flatMap(reservation -> reservation.instances().stream())
          .filter(instance -> instance.instanceId().equals(instanceId))
          .map(Instance::state)
          .findFirst();

      if (foundState.isEmpty()) {
        failWithNotFoundError(instanceId, desiredState);
      }

      InstanceStateName stateName = foundState.get().name();
      if (desiredState == stateName) {
        return true;
      } else {
        long waitTime = waitParameters.getStatusWaitStrategy().getWaitTime(attempt++);
        sleepQuietly(waitTime);
        long totalTimeWaited = System.currentTimeMillis() - started;
        if (totalTimeWaited > waitParameters.getOperationTimeout()) {
          return false;
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
