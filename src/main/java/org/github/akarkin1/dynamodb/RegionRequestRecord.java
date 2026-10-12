package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * An open request for a region without node infrastructure (pk {@code REGION_REQUEST}, sk region
 * id); deleted when a root user decides, otherwise expires by TTL.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
public class RegionRequestRecord {

  public static final String TYPE = "REGION_REQUEST";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  /** The AWS region id, e.g. {@code eu-central-1}. */
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String regionId;
  /** The requester's Telegram user id. */
  private Long userId;
  /** Nullable: not every Telegram user has a username. */
  private String username;
  private String firstName;
  /** Nullable: a Telegram user need not have a last name. */
  private String lastName;
  private String languageCode;
  /** Epoch millis. */
  private Long requestedAt;
  /** Epoch seconds, the table's TTL attribute. */
  private Long expiresAt;

}
