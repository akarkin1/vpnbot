Feature: Bot commands
  Background:
    Given the lambda environment is cleaned up

  Scenario: /supportedRegions command returns supported regions from config
    Given the config bucket contains file "config/vpn/supported-regions.txt" with content:
      """
      us-east-1
      eu-central-1
      """
    When user is authorized with role "READ_ONLY"
    When "/supportedRegions" command is sent to the bot
    Then the bot should reply with response:
      """
      Supported regions:
      	– Frankfurt (eu-central-1): vpn
      	– N. Virginia (us-east-1): vpn
      """
    And the lambda should return valid response

  Scenario: /supportedRegions command returns a proper message, when supported regions config is empty
    Given the config bucket contains file "config/vpn/supported-regions.txt" with content:
      """
      """
    When user is authorized with role "READ_ONLY"
    When "/supportedRegions" command is sent to the bot
    Then the bot should reply with response:
      """
      No regions supported.
      """
    And the lambda should return valid response

  Scenario: /runNodeIn command starts ECS task, shows in /listRunningNodes, and stops at scenario end
    Given the config bucket contains file "config/vpn/supported-regions.txt" with content:
      """
      us-east-1
      """
    And the config bucket contains file "config/us-east-1/vpn/stack-output-parameters.json" with content:
      """
      [
          {
              "OutputKey": "EcsTaskExecutionRoleArn",
              "OutputValue": "arn:aws:iam::123456789012:role/vpn-ecs-resources-cfn-TailscaleNodeEcsTaskExecution-abc123",
              "Description": "Role ARN for an ECS Task to create."
          },
          {
              "OutputKey": "EcsTaskDefinitionArn",
              "OutputValue": "arn:aws:ecs:us-east-1:123456789012:task-definition/vpn:1",
              "Description": "Arn of created ECS Task Definition."
          },
          {
              "OutputKey": "SecurityGroupId",
              "OutputValue": "sg-12345",
              "Description": "ID of the Subgroup, which an ECS Task will be launched with."
          },
          {
              "OutputKey": "EcsTaskRoleArn",
              "OutputValue": "arn:aws:iam::123456789012:role/vpn-ecs-resources-cfn-TailscaleNodeEcsTaskRole-abc123",
              "Description": "Role ARN for an ECS Task to create."
          },
          {
              "OutputKey": "EcrRepositoryUrl",
              "OutputValue": "123456789012.dkr.ecr.us-east-1.amazonaws.com/tailscale-node-ecr-repo",
              "Description": "Url of the created ECR repository, where Docker images with VPN Server will be stored."
          },
          {
              "OutputKey": "SubnetId",
              "OutputValue": "subnet-12345",
              "Description": "ID of the Subnet, where an ECS Task will be launched."
          },
          {
              "OutputKey": "EcsClusterName",
              "OutputValue": "test-cluster",
              "Description": "The name of ECS cluster, where a VPN task will be spin out."
          }
      ]
      """
    When user is authorized with role "NODE_ADMIN"
    When "/runNode vpn us-east-1" command is sent to the bot
    Then the bot should reply with response:
      """
      Starting VPN node in N. Virginia (us-east-1)...
      """
    When "/listRunningNodes" command is sent to the bot
    Then the bot should reply with response:
      """
      Running nodes:
      """

