#!/bin/sh

# Set the timeout in seconds
: "${INACTIVITY_TIMEOUT:=600}"  # 10 minutes
: "${STATUS_CHECK_INTERVAL:=60}"  # Check every minute

# Initialize a counter for inactive time
inactive_time=0

while true; do
    # Check the number of active connections
    ACTIVE_CONNECTIONS=$(tailscale status | grep 'Active' | wc -l)
    echo "Tailscale status: $(tailscale status --json)"

    if [ "$ACTIVE_CONNECTIONS" -eq 0 ]; then
        # Increment inactive time if there are no active connections
        inactive_time=$((inactive_time + STATUS_CHECK_INTERVAL))
        echo "No active connections. The node will be terminated in $((INACTIVITY_TIMEOUT-inactive_time)) seconds."

        if [ "$inactive_time" -ge "$INACTIVITY_TIMEOUT" ]; then
            echo "No active connections for $INACTIVITY_TIMEOUT seconds. Stopping the Tailscale task..."
            exit 0  # Exit the script, which will stop the container
        fi
    else
        echo "A user is connect to the Tailscale Node."
        # Reset the inactive time counter if there are active connections
        inactive_time=0
    fi

    # Wait for the specified check interval
    sleep $STATUS_CHECK_INTERVAL
done
