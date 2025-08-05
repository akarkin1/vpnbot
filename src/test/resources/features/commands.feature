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
