#!/usr/bin/env bash
# Prints the DynamoDB item (low-level JSON) of a supported region, built from the outputs of the
# region's ECS CloudFormation stack (`aws cloudformation describe-stacks --query 'Stacks[0].Outputs'`).
# Usage: region-item.sh <region> <stack-outputs.json>
# Fails (no output) if one of the required outputs is missing or empty.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <region> <stack-outputs.json>" >&2
  exit 2
fi

region=$1
outputs_file=$2

jq -e --arg region "$region" --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
  def output($key):
    (map(select(.OutputKey == $key)) | first | .OutputValue // "") as $value
    | if $value == "" then error("Stack output \($key) is missing or empty") else $value end;
  {
    pk: {S: "REGION"},
    sk: {S: $region},
    ecsClusterName: {S: output("EcsClusterName")},
    ecsTaskDefinitionArn: {S: output("EcsTaskDefinitionArn")},
    subnetId: {S: output("SubnetId")},
    securityGroupId: {S: output("SecurityGroupId")},
    updatedAt: {S: $now}
  }' "$outputs_file"
