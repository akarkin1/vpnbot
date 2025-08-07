Feature: Authentication

  Scenario Outline: Valid token allows access to commands
    Given a user <username> with a valid x-telegram-bot-api-secret-token
    When the user calls <command>
    Then the command is executed successfully

    Examples:
      | username   | command                |
      | root       | /help                  |
      | root       | /listRegisteredUsers   |
      | root       | /assignRoles           |
      | node_admin | /listRunningNodes      |
      | node_admin | /runNode vpn us-east-1 |

  Scenario Outline: Missing or invalid token denies access
    Given a user <username> with <tokenType> x-telegram-bot-api-secret-token
    When the user calls <command>
    Then access is denied

    Examples:
      | username   | tokenType   | command                |
      | root       | missing     | /help                  |
      | root       | invalid     | /listRegisteredUsers   |
      | node_admin | missing     | /listRunningNodes      |
      | node_admin | invalid     | /runNode vpn us-east-1 |

