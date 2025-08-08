@Onboarding
Feature: User Onboarding
  Background:
    Given no events sent to lambda
    And no ecs task run

  Scenario: Root user is present by default
    Given the system is initialized with a root user
    When the user "root" calls "/listRegisteredUsers"
    Then the registered users list contains "root" with root permission

  Scenario Outline: Root can onboard other users with any role
    Given the system is initialized with a root user
    When the user "root" assigns role <role> to user <username>
    And the user "root" calls "/listRegisteredUsers"
    Then the registered users list contains "<username>" with role "<role>"

    Examples:
      | role         | username      |
      | USER_ADMIN   | user_admin    |
      | READ_ONLY    | read_only     |
      | NODE_ADMIN   | node_admin    |

  # ToDo: Fix me
  Scenario Outline: /help shows only allowed commands per user
    Given the system is initialized with a root user
    And the user "root" assigns role <role> to user <username>
    When the user "<username>" calls "/help"
    Then the help command output contains only allowed commands for role <role>

    Examples:
      | role         | username      |
      | ROOT_ACCESS  | root          |
      | USER_ADMIN   | user_admin    |
      | READ_ONLY    | read_only     |
      | NODE_ADMIN   | node_admin    |

