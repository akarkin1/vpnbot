package org.github.akarkin1.ecs;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import software.amazon.awssdk.regions.Region;


@Getter
@Builder
@ToString
public class TaskInfo {

  private String id;
  private String hostName;
  private String state;
  private String cluster;
  private String publicIp;
  private String location;
  private Region region;

}
