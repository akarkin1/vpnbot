package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * A pending access request of a user without access (pk {@code ACCESS_REQUEST}, sk user id);
 * deleted when an admin decides, otherwise expires by TTL.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
public class AccessRequestRecord {

  public static final String TYPE = "ACCESS_REQUEST";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  /** Telegram user id as a decimal string. */
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String userId;
  /** Nullable: not every Telegram user has a username. */
  private String username;
  private String firstName;
  private String languageCode;
  /** Epoch millis. */
  private Long requestedAt;
  /** Epoch seconds, the table's TTL attribute. */
  private Long expiresAt;

}
