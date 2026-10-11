package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Set;

/**
 * A user's permissions (pk {@code USER}, sk Telegram user id); a user without permissions has no
 * record.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
public class UserRecord {

  public static final String TYPE = "USER";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  /** Telegram user id as a decimal string. */
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String userId;
  /** Telegram username (without {@code @}) for display, may be null. */
  private String username;
  /** String Set of {@code Permission} names, never empty. */
  private Set<String> permissions;

}
