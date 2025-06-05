#!/bin/bash
set -e

# Define S3 bucket name and backup interval
S3_BUCKET="minecraft-s3"
MINECRAFT_DATA_DIR="/data"
BACKUP_INTERVAL_SECONDS=3600  # Backup every hour

# Function to perform backup
perform_backup() {
    echo "$(date): Starting backup to S3..."
    
    # Ensure the Minecraft server saves all current data
    if [ -n "${RCON_PASSWORD}" ]; then
        echo "Saving world data via RCON..."
        mcrcon -H localhost -P ${RCON_PORT:-25575} -p "${RCON_PASSWORD}" "save-all"
        # Wait a moment for the save to complete
        sleep 15
    fi
    
    # Sync the data directory to S3
    echo "Syncing data to S3..."
    aws s3 sync ${MINECRAFT_DATA_DIR}/ s3://${S3_BUCKET}/world-backup/ \
        --exclude "*.log" \
        --exclude "*.gz" \
        --exclude "crash-reports/*" \
        --exclude "logs/*" \
        --delete
    
    echo "$(date): Backup completed."
}

# Main backup loop
echo "Starting backup service. Will backup every ${BACKUP_INTERVAL_SECONDS} seconds."
while true; do
    # Perform backup
    perform_backup
    
    # Wait for the next backup interval
    echo "Next backup scheduled in ${BACKUP_INTERVAL_SECONDS} seconds."
    sleep ${BACKUP_INTERVAL_SECONDS}
done