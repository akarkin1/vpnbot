Feature: Permission Boundaries

  Background:
    Given no events sent to lambda
    And no ecs task run

  Scenario: User Story 1 - read_only user can only see vpn nodes
    Given root registers read_only user with role READ_ONLY and access to service vpn
    When root runs vpn and other nodes
    Then read_only can only see vpn nodes in /listRunningNodes
    And read_only cannot run any nodes

  Scenario: User Story 2 - read_write user can run vpn nodes, list others
    Given root registers read_write user with role NODE_ADMIN and access to service vpn
    When root runs vpn and other nodes
    Then read_write can see both vpn and other nodes in /listRunningNodes
    And read_write can run vpn nodes
    And read_write cannot run other nodes

  Scenario: User Story 3 - user_manager can register/delete users, not root
    Given root registers user_manager with role USER_ADMIN
    When user_manager registers and deletes users
    Then the users are updated accordingly in /listRegisteredUsers
    And user_manager cannot delete root

  Scenario Outline: Negative cases for permission boundaries
    Given a user <username> with role <role> and access to service <service>
    When the user tries to execute <command>
    Then the command is denied

    Examples:
      | username     | role        | service | command                |
      | read_only    | READ_ONLY   | vpn     | /runNode vpn us-east-1 |
      | read_write   | NODE_ADMIN  | vpn     | /runNode other us-east-1 |
      | user_manager | USER_ADMIN  |         | /deleteUsers root      |

