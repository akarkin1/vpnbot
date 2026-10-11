package org.github.akarkin1.ecs;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import software.amazon.awssdk.regions.Region;


@Getter
@Builder
@ToString
public class EcsNode {

  private String id;
  private String hostName;
  private String state;
  private String cluster;
  private String publicIp;
  private String location;
  private Region region;
  /** The owner's Telegram user id (the username for nodes started before Phase 4). */
  private String runBy;
  /** The owner's Telegram username, for display; may be null. */
  private String runByName;
  private String chatId;
  private String languageCode;

}
