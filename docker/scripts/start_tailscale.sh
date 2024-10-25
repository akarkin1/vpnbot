#!/bin/bash

TS_AUTH_KEY=$(aws secretsmanager get-secret-value --secret-id "${TAILSCALE_TOKEN_SECRET_ID}" --region "${TAILSCALE_TOKEN_SECRET_REGION}" --query SecretString --output text 2>/dev/null)

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

# ToDo: Left for debug only, need to be removed
sleep 0.5
echo "Tailscale status debug – status command: $(tailscale status)"
echo "Tailscale status debug – logged in? $(tailscale status | grep "logged in")"

# Start monitoring connections
/usr/local/bin/monitor_connections.sh