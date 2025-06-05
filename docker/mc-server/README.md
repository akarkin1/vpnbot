# Minecraft Server on ECS

This directory contains the necessary files to run a Minecraft server on AWS ECS with automatic backups to S3 and idle shutdown.

## Features

- Based on the official `itzg/minecraft-server` Docker image
- Automatic data backup to S3
- Data recovery from S3 on startup
- Automatic server shutdown when idle
- Whitelist and admin (ops) configuration
- RCON enabled for server management

## Configuration

### Environment Variables

The following environment variables can be set when launching the ECS task:

- `RCON_PASSWORD`: Password for RCON access (required)
- `RCON_PORT`: Port for RCON (default: 25575)
- `TYPE`: Minecraft server type (default: VANILLA)
- `BACKUP_INTERVAL_SECONDS`: Interval between backups in seconds (default: 3600 - 1 hour)
- `IDLE_THRESHOLD_SECONDS`: Time in seconds before shutting down when idle (default: 1800 - 30 minutes)

### S3 Bucket

The server uses an S3 bucket named `minecraft-s3` for backups. Make sure this bucket exists and the ECS task has appropriate permissions to access it.

### Player Configuration

By default, the server is configured with:
- A whitelist containing one player: MartyByrde
- Admin (ops) privileges for MartyByrde with permission level 4

The operator permission levels in Minecraft are:
- Level 1: Can bypass spawn protection
- Level 2: Level 1 + can use commands like /clear, /difficulty, /gamemode, /gamerule, /give, /tp
- Level 3: Level 2 + can use commands like /ban, /deop, /kick, /op
- Level 4: Level 3 + can use /stop (to stop the server)

The UUIDs in the configuration files (whitelist.json and ops.json) are placeholder values and should be replaced with real player UUIDs in a production environment. You can obtain a player's UUID using online tools like https://mcuuid.net/ or by checking the Minecraft server logs when a player joins.

To modify these defaults, edit the `entrypoint.sh` script.

## Scripts

### entrypoint.sh

The main entry point for the container. It:
- Restores data from S3 if available
- Sets up player permissions (whitelist and ops)
- Configures server properties
- Starts the backup and monitoring scripts
- Launches the Minecraft server
- Handles SIGTERM signals to ensure data is backed up before container termination

The script is designed to remain as PID 1 in the container, which allows it to properly receive and handle SIGTERM signals from AWS ECS when the container is being terminated. When a SIGTERM signal is received, the script:
1. Tells the Minecraft server to save all current data
2. Performs a final backup to S3
3. Terminates all background processes
4. Exits gracefully

### backup.sh

Handles data backups to S3:
- Performs regular backups at the specified interval
- Handles SIGTERM signals to ensure data is backed up before container termination

### monitor.sh

Monitors player activity and manages server shutdown:
- Checks for connected players at regular intervals
- Tracks idle time when no players are connected
- Shuts down the server when idle for too long

## AWS ECS Setup

To run this container on AWS ECS:

1. Build and push the Docker image to a container registry
2. Create an ECS task definition with:
   - 20GB ephemeral storage
   - Environment variables for configuration
   - IAM role with S3 access permissions
3. Configure the task to use the container image
4. Launch the task on ECS

## Local Testing

To test locally:

```bash
docker build -t minecraft-server .
docker run -e RCON_PASSWORD=your_password -p 25565:25565 -p 25575:25575 minecraft-server
```

## Notes

- The server automatically accepts the Minecraft EULA
- All world data is stored in the container's `/data` directory
- Logs, crash reports, and other temporary files are excluded from backups
