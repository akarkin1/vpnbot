package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/** Marks a Telegram update as received (pk {@code TG_UPDATE_LOCK}, sk update_id); expires by TTL. */
@DynamoDbBean
@Data
@NoArgsConstructor
public class TgUpdateLock {

  public static final String TYPE = "TG_UPDATE_LOCK";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  /** Telegram update_id as a decimal string. */
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String updateId;
  /** Epoch seconds, the table's TTL attribute. */
  private Long expiresAt;
  /** Epoch millis. */
  private Long receivedAt;

}
