@Authentication
Feature: Authentication
  Background:
    Given wiremock requests are reset

  Scenario: Valid token allows access and returns registered users list
    Given the system is initialized with a root user
    And a user root with a valid x-telegram-bot-api-secret-token
    When the user calls /listRegisteredUsers
    Then the registered users list contains "root" with root permission

  Scenario: Invalid token denies access
    Given a user root with invalid x-telegram-bot-api-secret-token
    When the user calls /listRegisteredUsers
    Then access is denied
