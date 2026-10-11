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
 * The permissions of a user pre-registered by username who has not contacted the bot yet
 * (pk {@code PENDING_USER}, sk username as typed by the admin, without {@code @}). Becomes a
 * {@link UserRecord} at the user's first request.
 *
 * @deprecated Pre-registration by username ended with 2026.10. Remove once
 *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
 *     docs/specs/cleanup-2026-10.md §4.3.
 */
@Deprecated(forRemoval = true)
@DynamoDbBean
@Data
@NoArgsConstructor
public class PendingUserRecord {

  public static final String TYPE = "PENDING_USER";

  @Getter(onMethod_ = {@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})
  private String type = TYPE;
  @Getter(onMethod_ = {@DynamoDbSortKey, @DynamoDbAttribute("sk")})
  private String username;
  /** String Set of {@code Permission} names, never empty. */
  private Set<String> permissions;

}
