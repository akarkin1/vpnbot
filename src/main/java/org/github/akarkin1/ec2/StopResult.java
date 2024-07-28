package org.github.akarkin1.ec2;

public enum StopResult {
  STOP_SUCCEED,
  INSTANCE_NOT_FOUND,
  ALREADY_STOPPED,
  NOT_RUN,
  STOP_WAIT_TIMEOUT,
  UNKNOWN
}
