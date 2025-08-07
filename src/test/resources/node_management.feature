Feature: Node Management

  Background:
    Given no events sent to lambda
    And no ecs task run

  Scenario Outline: User runs a node and it appears in the list
    Given a user <username> with permission to run nodes for service <service>
    When the user runs a node in region <region>
    Then the node appears in /listRunningNodes with name <service>-<username>-<city>-<nodeNumber>, a nonempty IP address, status HEALTHY, and region <region>

    Examples:
      | username   | service | region      | city      | nodeNumber |
      | root       | vpn     | us-east-1   | virginia  | 1          |
      | node_admin | vpn     | us-east-1   | virginia  | 1          |
      | root       | other   | us-central-1| chicago   | 1          |

  Scenario Outline: Node naming follows correct pattern and sequential numbering
    Given there are existing nodes for service <service> in region <region>
    When user <username> runs another node for service <service> in region <region>
    Then the new node name is assigned sequentially as <service>-<username>-<city>-<nextNodeNumber>

    Examples:
      | username   | service | region      | city      | nextNodeNumber |
      | root       | vpn     | us-east-1   | virginia  | 2              |
      | node_admin | vpn     | us-east-1   | virginia  | 2              |

  Scenario Outline: Node name reuse after stop
    Given a user <username> with permission to run nodes for service <service>
    And the user runs a node in region <region> with name <service>-<username>-<city>-<nodeNumber>
    And the node <service>-<username>-<city>-<nodeNumber> was stopped
    When a new node is run for service <service> in region <region> by user <username>
    Then the stopped node's name <service>-<username>-<city>-<nodeNumber> can be reused

    Examples:
      | service | username   | city      | region      | nodeNumber |
      | vpn     | root       | virginia  | us-east-1   | 1          |
      | other   | root       | chicago   | us-central-1| 1          |

  Scenario Outline: Negative case - running node without permission
    Given a user <username> without permission to run nodes
    When the user tries to run a node for service <service> in region <region>
    Then the command is denied

    Examples:
      | username   | service | region      |
      | read_only  | vpn     | us-east-1   |
      | read_only  | other   | us-central-1|

  Scenario Outline: /runNode command syntax for single and multiple services
    Given a user <username> entitled to <serviceCount> service(s): <services>
    When the user runs <syntax>
    Then the node is created for the correct service and region

    Examples:
      | username   | serviceCount | services      | syntax                  |
      | vpn_user   | 1            | vpn          | /runNode us-east-1      |
      | vpn_user   | 1            | vpn          | /runNode vpn us-east-1  |
      | multi_user | 2            | vpn,other    | /runNode vpn us-east-1  |
      | multi_user | 2            | vpn,other    | /runNode other us-central-1 |
