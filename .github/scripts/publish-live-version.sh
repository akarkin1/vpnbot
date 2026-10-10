#!/usr/bin/env bash
# Publishes a new version of the Lambda from $LATEST (code and configuration as deployed right now;
# with SnapStart this takes the snapshot) and points the alias at it once the version is active.
# Usage: publish-live-version.sh <function-name> <alias>
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <function-name> <alias>" >&2
  exit 2
fi

function_name=$1
alias_name=$2

# A code or configuration update may still be in progress
aws lambda wait function-updated-v2 --function-name "$function_name"

version=$(aws lambda publish-version --function-name "$function_name" --query 'Version' --output text)
echo "Published version $version of $function_name"

# With SnapStart the version is Pending until its snapshot is taken; the alias must not point at it before
aws lambda wait function-active-v2 --function-name "$function_name" --qualifier "$version"

aws lambda update-alias --function-name "$function_name" --name "$alias_name" --function-version "$version" > /dev/null
echo "Alias $alias_name -> version $version"
