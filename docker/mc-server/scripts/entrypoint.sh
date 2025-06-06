#!/bin/bash
set -e

# Define S3 bucket name and data directory
S3_BUCKET=${S3_BUCKET_NAME:-"akarkin1-minecraf-s3"}
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

        # Fix permissions after restore
        echo "Fixing permissions on restored data..."
        chmod -R 755 ${MINECRAFT_DATA_DIR}

        echo "World data restored from S3."
    else
        echo "No existing world data found in S3."
    fi
}

# Main execution
echo "Starting Minecraft server setup..."

# Restore from S3 if data exists
restore_from_s3

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
