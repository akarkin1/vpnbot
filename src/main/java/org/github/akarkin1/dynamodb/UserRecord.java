package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Set;

/** A user's permissions (pk {@code USER}, sk Telegram username); a user without permissions has no record. */
@DynamoDbBean
@Data
@NoArgsConstructor
public class UserRecord {

  public static final String TYPE = "USER";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String username;
  /** String Set of {@code Permission} names, never empty. */
  private Set<String> permissions;

}
