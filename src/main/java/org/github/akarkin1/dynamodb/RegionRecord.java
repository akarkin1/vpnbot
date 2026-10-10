package org.github.akarkin1.dynamodb;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/** A supported region with the outputs of its node stack (pk {@code REGION}, sk region id). */
@DynamoDbBean
@Data
@NoArgsConstructor
public class RegionRecord {

  public static final String TYPE = "REGION";

  private String type = TYPE;
  private String regionId;
  private String ecsClusterName;
  private String ecsTaskDefinitionArn;
  private String subnetId;
  private String securityGroupId;
  private String updatedAt;

  @DynamoDbPartitionKey
  @DynamoDbAttribute("pk")
  public String getType() {
    return type;
  }

  @DynamoDbSortKey
  @DynamoDbAttribute("sk")
  public String getRegionId() {
    return regionId;
  }

}
