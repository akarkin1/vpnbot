#!/bin/bash
set -e

# Configuration
CHECK_INTERVAL_SECONDS=${CHECK_INTERVAL_SECONDS:-300}  # Default: Check every 5 minutes
IDLE_THRESHOLD_SECONDS=${IDLE_THRESHOLD_SECONDS:-1800}  # Default: Shutdown after 30 minutes of inactivity
MINECRAFT_DATA_DIR="/data"
LAST_ACTIVE_FILE="${MINECRAFT_DATA_DIR}/.last_active"

# Initialize last active timestamp
echo $(date +%s) > ${LAST_ACTIVE_FILE}

# Function to check for connected players
check_players() {
    if [ -z "${RCON_PASSWORD}" ]; then
        echo "RCON_PASSWORD not set. Cannot check for players."
        return 1
    fi

    # Get list of players via RCON
    PLAYER_LIST=$(mcrcon -H localhost -P ${RCON_PORT:-25575} -p "${RCON_PASSWORD}" "list")
    echo "Player status: ${PLAYER_LIST}"

    # Check if there are players online
    if [[ "${PLAYER_LIST}" == *"There are 0 of"* ]]; then
        echo "No players connected."
        return 1
    else
        echo "Players are connected."
        return 0
    fi
}

# Function to update last active timestamp
update_last_active() {
    echo $(date +%s) > ${LAST_ACTIVE_FILE}
    echo "Updated last active timestamp: $(cat ${LAST_ACTIVE_FILE})"
}

# Function to check idle time
check_idle_time() {
    CURRENT_TIME=$(date +%s)
    LAST_ACTIVE=$(cat ${LAST_ACTIVE_FILE})
    IDLE_TIME=$((CURRENT_TIME - LAST_ACTIVE))

    echo "Current idle time: ${IDLE_TIME} seconds (threshold: ${IDLE_THRESHOLD_SECONDS} seconds)"

    if [ ${IDLE_TIME} -ge ${IDLE_THRESHOLD_SECONDS} ]; then
        echo "Server has been idle for ${IDLE_TIME} seconds, exceeding threshold of ${IDLE_THRESHOLD_SECONDS} seconds."
        return 0
    else
        return 1
    fi
}

# Function to shutdown the server
shutdown_server() {
    echo "Initiating server shutdown due to inactivity..."

    # Notify any remaining players (just in case)
    if [ -n "${RCON_PASSWORD}" ]; then
        mcrcon -H localhost -P ${RCON_PORT:-25575} -p "${RCON_PASSWORD}" "say Server shutting down due to inactivity in 10 seconds."
        sleep 10
    fi

    # Send SIGTERM to the main process (PID 1) which will trigger our backup script via trap
    echo "Sending SIGTERM to initiate shutdown and backup..."
    kill -TERM 1
}

# Main monitoring loop
echo "Starting player monitoring service. Will check every ${CHECK_INTERVAL_SECONDS} seconds."
echo "Server will shut down after ${IDLE_THRESHOLD_SECONDS} seconds of inactivity."

# Wait for server to fully start before monitoring
echo "Waiting for server to start up..."
sleep 60

while true; do
    echo "$(date): Checking for connected players..."

    # Check if server is running
    if ! pgrep -f "java.*minecraft_server" > /dev/null; then
        echo "Minecraft server process not found. Exiting monitor."
        exit 1
    fi

    # Check for players
    if check_players; then
        # Players connected, update last active time
        update_last_active
    else
        # No players connected, check idle time
        if check_idle_time; then
            # Idle time exceeded threshold, shutdown server
            shutdown_server
            # Exit the script after initiating shutdown
            exit 0
        fi
    fi

    # Wait for next check
    echo "Next check in ${CHECK_INTERVAL_SECONDS} seconds."
    sleep ${CHECK_INTERVAL_SECONDS}
done
