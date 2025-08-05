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
    Then the lambda should return valid response
#    Then the bot should reply with:
#      """
#      Supported regions:
#        - N. Virginia (us-east-2): vpn
#        - Frankfurt (eu-central-1): vpn
#      """
    And the lambda environment is cleaned up

