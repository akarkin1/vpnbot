package org.github.akarkin1.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.github.akarkin1.config.model.CfnStackOutputParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilsTest {

  @Test
  void testParseList() {
    String json = """
        [
            {
                "OutputKey": "EcsTaskExecutionRoleArn",
                "OutputValue": "arn:aws:iam::${AWS::AccountId}:role/vpn-ecs-resources-cfn-TailscaleNodeEcsTaskExecution-1",
                "Description": "Role ARN for an ECS Task to create."
            }
        ]
        """;
    List<CfnStackOutputParameter> stackOutputParameters = JsonUtils.parseJson(
        json,
        new TypeReference<>() {});
    assertEquals(1, stackOutputParameters.size());
    assertEquals("EcsTaskExecutionRoleArn", stackOutputParameters.getFirst().outputKey());
    assertEquals(
        "arn:aws:iam::${AWS::AccountId}:role/vpn-ecs-resources-cfn-TailscaleNodeEcsTaskExecution-1",
        stackOutputParameters.getFirst().outputValue());
  }

}