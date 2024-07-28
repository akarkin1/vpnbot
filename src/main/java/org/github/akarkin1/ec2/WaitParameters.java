package org.github.akarkin1.ec2;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitParameters {

  private long operationTimeout;
  private WaitStrategy statusWaitStrategy;

}
