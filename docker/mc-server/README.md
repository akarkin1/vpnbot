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
- `S3_BUCKET_NAME`: Name of the S3 bucket for backups (default: akarkin1-minecraf-s3)
- `S3_BACKUP_PATH`: Path within the S3 bucket for backups (default: world-backup)
- `BACKUP_INTERVAL_SECONDS`: Interval between backups in seconds (default: 3600 - 1 hour)
- `IDLE_THRESHOLD_SECONDS`: Time in seconds before shutting down when idle (default: 1800 - 30 minutes)
- `CHECK_INTERVAL_SECONDS`: Interval between player checks in seconds (default: 300 - 5 minutes)

### S3 Bucket

The server uses an S3 bucket for backups, which can be configured using the `S3_BUCKET_NAME` and `S3_BACKUP_PATH` environment variables. Make sure this bucket exists and the ECS task has appropriate permissions to access it.

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
- Relies on environment variables for server properties configuration
- Starts the backup and monitoring scripts
- Launches the Minecraft server
- Handles SIGTERM signals to ensure data is backed up before container termination

> **Note:** The script does not directly modify server.properties. Instead, it relies on the base image's mechanism of using environment variables to configure the server. This avoids permission issues that can occur when trying to modify server.properties directly.

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

> **Note:** The script stores the last active timestamp in /tmp/.minecraft_last_active rather than in the /data directory to avoid permission issues.

## AWS ECS Setup

### CloudFormation Deployment

A CloudFormation template is provided in `cloudformation/ecs-mc-server.yml` to automate the deployment of the Minecraft server on AWS ECS. The template creates:

1. VPC with a public subnet
2. Security groups for Minecraft and RCON ports
3. S3 bucket for world backups
4. ECR repository for the Docker image
5. ECS cluster and task definition with 20GB ephemeral storage
6. IAM roles with necessary permissions

To deploy the CloudFormation stack:

```bash
aws cloudformation create-stack \
  --stack-name minecraft-server \
  --template-body file://cloudformation/ecs-mc-server.yml \
  --parameters \
    ParameterKey=RconPassword,ParameterValue=your_secure_password \
    ParameterKey=S3BucketName,ParameterValue=your-minecraft-bucket-name \
  --capabilities CAPABILITY_IAM
```

### Running the Server

After deploying the CloudFormation stack, you need to:

1. Build and push the Docker image to the created ECR repository
2. Use the provided scripts to start and stop the Minecraft server

#### Starting the Server

Use the `cloudformation/start-minecraft-server.sh` script to start the server:

```bash
./cloudformation/start-minecraft-server.sh \
  --subnet-id subnet-xxxxxxxx \
  --security-group-id sg-xxxxxxxx
```

You can get the subnet ID and security group ID from the CloudFormation stack outputs.

#### Stopping the Server

Use the `cloudformation/stop-minecraft-server.sh` script to stop the server:

```bash
./cloudformation/stop-minecraft-server.sh
```

The script will list running tasks and prompt you to select one to stop.

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

## Troubleshooting

### Permission Issues

If you encounter permission errors like `java.nio.file.AccessDeniedException: /data/server.properties`, this is because the base image (itzg/minecraft-server) expects to manage server.properties itself based on environment variables. The scripts have been designed to avoid directly modifying files in the /data directory to prevent these permission issues:

1. Server properties are configured through environment variables rather than direct file modifications
2. Temporary files (like the last active timestamp) are stored in /tmp rather than in the /data directory

If you need to customize server properties, use the appropriate environment variables as documented in the [itzg/minecraft-server documentation](https://github.com/itzg/docker-minecraft-server).
