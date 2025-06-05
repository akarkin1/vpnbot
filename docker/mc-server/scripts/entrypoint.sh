#!/bin/bash
set -e

# Define S3 bucket name and data directory
S3_BUCKET=${S3_BUCKET_NAME:-"minecraft-s3"}
S3_BACKUP_PATH=${S3_BACKUP_PATH:-"world-backup"}
MINECRAFT_DATA_DIR="/data"
WORLD_DIR="${MINECRAFT_DATA_DIR}/world"

# Function to perform final backup
perform_final_backup() {
    echo "Received SIGTERM. Performing final backup before shutdown..."

    # Ensure the Minecraft server saves all current data
    if [ -n "${RCON_PASSWORD}" ]; then
        echo "Saving world data via RCON..."
        mcrcon -H localhost -P ${RCON_PORT:-25575} -p "${RCON_PASSWORD}" "save-all"
        # Wait a moment for the save to complete
        sleep 15
    fi

    # Sync the data directory to S3
    echo "Syncing data to S3..."
    aws s3 sync ${MINECRAFT_DATA_DIR}/ s3://${S3_BUCKET}/${S3_BACKUP_PATH}/ \
        --exclude "*.log" \
        --exclude "*.gz" \
        --exclude "crash-reports/*" \
        --exclude "logs/*" \
        --delete

    echo "Final backup completed. Shutting down..."

    # Kill background processes
    kill $(jobs -p) 2>/dev/null || true

    exit 0
}

# Register the SIGTERM handler
trap perform_final_backup SIGTERM

# Function to restore data from S3 if available
restore_from_s3() {
    echo "Checking for existing world data in S3..."
    if aws s3 ls s3://${S3_BUCKET}/${S3_BACKUP_PATH}/ &>/dev/null; then
        echo "Found world backup in S3, restoring..."
        aws s3 sync s3://${S3_BUCKET}/${S3_BACKUP_PATH}/ ${MINECRAFT_DATA_DIR}/
        echo "World data restored from S3."
    else
        echo "No existing world data found in S3."
    fi
}

# Function to set up whitelist and ops
setup_player_permissions() {
    # Create whitelist.json if it doesn't exist
    if [ ! -f "${MINECRAFT_DATA_DIR}/whitelist.json" ]; then
        echo "Creating whitelist.json..."
        # Note: These UUIDs are placeholders and should be replaced with real player UUIDs in production
        # You can obtain real UUIDs from https://mcuuid.net/ or from server logs when players join
        echo '[
            {"uuid": "d0d1aa3c-20ac-4167-8ba7-7b4862d9cbaf", "name": "MartyByrde"}
        ]' > "${MINECRAFT_DATA_DIR}/whitelist.json"
    fi

    # Create ops.json if it doesn't exist
    if [ ! -f "${MINECRAFT_DATA_DIR}/ops.json" ]; then
        echo "Creating ops.json..."
        # Using the same UUID for admin1 as in the whitelist
        # The "level" parameter determines operator permission level (1-4):
        # - Level 1: Can bypass spawn protection
        # - Level 2: Level 1 + can use commands like /clear, /difficulty, /gamemode, /gamerule, /give, /tp
        # - Level 3: Level 2 + can use commands like /ban, /deop, /kick, /op
        # - Level 4: Level 3 + can use /stop (to stop the server)
        echo '[
            {"uuid": "d0d1aa3c-20ac-4167-8ba7-7b4862d9cbaf", "name": "MartyByrde", "level": 4, "bypassesPlayerLimit": true}
        ]' > "${MINECRAFT_DATA_DIR}/ops.json"
    fi
}

# Function to set up server properties
setup_server_properties() {
    # Create server.properties if it doesn't exist or update it
    if [ ! -f "${MINECRAFT_DATA_DIR}/server.properties" ]; then
        echo "Creating server.properties..."
        touch "${MINECRAFT_DATA_DIR}/server.properties"
    fi

    # Set RCON properties
    if [ -n "${RCON_PASSWORD}" ]; then
        echo "Setting RCON password..."
        sed -i '/rcon.password=/d' "${MINECRAFT_DATA_DIR}/server.properties"
        echo "rcon.password=${RCON_PASSWORD}" >> "${MINECRAFT_DATA_DIR}/server.properties"
    else
        echo "WARNING: RCON_PASSWORD environment variable not set. Using default password."
    fi

    # Ensure whitelist is enforced
    sed -i '/enforce-whitelist=/d' "${MINECRAFT_DATA_DIR}/server.properties"
    echo "enforce-whitelist=true" >> "${MINECRAFT_DATA_DIR}/server.properties"

    sed -i '/white-list=/d' "${MINECRAFT_DATA_DIR}/server.properties"
    echo "white-list=true" >> "${MINECRAFT_DATA_DIR}/server.properties"
}

# Main execution
echo "Starting Minecraft server setup..."

# Restore from S3 if data exists
restore_from_s3

# Set up player permissions
setup_player_permissions

# Set up server properties
setup_server_properties

# Start the backup and monitoring scripts in the background
echo "Starting backup and monitoring scripts..."
/scripts/backup.sh &
/scripts/monitor.sh &

# Start the original entrypoint and wait for it
echo "Starting Minecraft server..."
/start &
MINECRAFT_PID=$!

# Wait for the Minecraft server process
wait $MINECRAFT_PID
