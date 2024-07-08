package org.github.akarkin1.exception;

import lombok.Getter;

@Getter
public class InstanceNotFoundException extends RuntimeException {
  private final String instanceId;

  public InstanceNotFoundException(String instanceId) {
    super("No instance found with ID: " + instanceId);
    this.instanceId = instanceId;
  }
}
