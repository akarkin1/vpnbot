package org.github.akarkin1.ecs;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
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
  private String region;
  private List<Tag> tags;

  public record Tag(String key, String value) {}
}