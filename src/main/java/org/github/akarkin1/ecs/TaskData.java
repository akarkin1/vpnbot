package org.github.akarkin1.ecs;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import software.amazon.awssdk.services.ecs.model.Tag;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@ToString
public class TaskData {

  private String taskId;
  private String taskArn;
  private String cluster;
  private String taskDefinition;
  private String lastStatus;
  private String desiredStatus;
  private String createdAt;
  private String stoppedAt;
  private String assignedIp;
  private List<Tag> tags;
}