Feature: Sample Lambda E2E
  Scenario: Lambda responds to a simple request
    Given the S3 bucket is available
    When I invoke the lambda with a basic request
    Then the response should contain status 200

