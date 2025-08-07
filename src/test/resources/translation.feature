Feature: Command Translation

  Scenario Outline: Commands are translated and contain no placeholders
    Given a user sends a command <command> with lang_code=<lang_code>
    When the command is executed
    Then the response is in <language>
    And the response contains no placeholders

    Examples:
      | command           | lang_code | language |
      | /help             | ru-RU     | Russian  |
      | /listRegisteredUsers | ru-RU     | Russian  |
      | /help             | en-US     | English  |
      | /listRegisteredUsers | en-US     | English  |

