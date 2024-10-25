#!/bin/bash

TS_AUTH_KEY=$(aws secretsmanager get-secret-value --secret-id "${TAILSCALE_TOKEN_SECRET_ID}" --region "${TAILSCALE_TOKEN_SECRET_REGION}" --query SecretString --output text 2>/dev/null)

# ToDo: delete it, added for debug.
aws secretsmanager get-secret-value --secret-id "${TAILSCALE_TOKEN_SECRET_ID}" --region "${TAILSCALE_TOKEN_SECRET_REGION}" --query SecretString --output text

if [ $? -ne 0 ] || [ -z "$TS_AUTH_KEY" ]; then
  echo "Error: Tailscale auth key could not be retrieved."
  exit 1
fi

function up() {
    until tailscale up --authkey="${TS_AUTH_KEY}" --hostname="${TAILSCALE_HOSTNAME}" --advertise-exit-node
    do
        sleep 0.1
    done
}

# send this function into the background
up &

# Start Tailscale
tailscaled --tun=userspace-networking --no-logs-no-support &

# Start monitoring connections
/usr/local/bin/monitor_connections.sh