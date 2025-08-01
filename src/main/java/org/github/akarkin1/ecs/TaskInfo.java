package org.github.akarkin1.ecs;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import software.amazon.awssdk.regions.Region;


@Getter
@Builder
@ToString
public class TaskInfo {

  private java.lang.String id;
  private java.lang.String hostName;
  private java.lang.String state;
  private java.lang.String cluster;
  private java.lang.String publicIp;
  private java.lang.String location;
  private Region region;
  private String serviceName;

}
