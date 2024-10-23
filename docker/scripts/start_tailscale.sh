#!/bin/bash

TS_AUTH_KEY=$(aws secretsmanager get-secret-value --secret-id "${TAILSCALE_TOKEN_SECRET_ID}" --query SecretString --output text 2>/dev/null)

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

# Start Tailscale with the specified hostname
exec tailscaled --tun=userspace-networking --loglevel=error

# Start monitoring connections
/usr/local/bin/monitor_connections.sh