package org.github.akarkin1.ec2;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceInfo {
  private String name;
  private String id;
  private String state;
  private String publicIp;
  private String location;
}
