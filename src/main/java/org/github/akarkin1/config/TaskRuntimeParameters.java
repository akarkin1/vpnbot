package org.github.akarkin1.config;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class TaskRuntimeParameters {

  private String ecsClusterName;
  private String ecsTaskDefinition;
  private String subnetId;
  private String securityGroupId;

}
