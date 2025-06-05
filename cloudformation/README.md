# Minecraft Server CloudFormation Resources

This directory contains CloudFormation templates and scripts for deploying and managing a Minecraft server on AWS ECS.

## Files

- `cloudformation/ecs-mc-server.yml`: CloudFormation template for deploying the Minecraft server infrastructure
- `scripts/start-minecraft-server.sh`: Script for starting the Minecraft server ECS task
- `scripts/stop-minecraft-server.sh`: Script for stopping the Minecraft server ECS task

## CloudFormation Template

The `ecs-mc-server.yml` template creates the following resources:

- VPC with a public subnet (CIDR block: 10.2.0.0/28 by default)
- Security groups for Minecraft (25565) and RCON (25575) ports
- S3 bucket for world backups
- ECR repository for the Minecraft server Docker image
- ECS cluster and task definition with 20GB ephemeral storage
- IAM roles with necessary permissions for S3 access, CloudWatch logs, and networking

### Parameters

The template accepts the following parameters:

| Parameter             | Description                            | Default                   |
|-----------------------|----------------------------------------|---------------------------|
| ClientIPCIDR          | CIDR IP to be granted access by the SG | 0.0.0.0/0                 |
| VpcCidrBlock          | CIDR block for the VPC                 | 10.2.0.0/28               |
| EcsClusterName        | Name of the ECS Cluster                | minecraft-server-cluster  |
| EcrRepositoryName     | Name of the ECR repository             | minecraft-server-repo     |
| EcsTaskDefinitionName | Task Definition name                   | minecraft-server-task-def |
| S3BucketName          | Name of the S3 bucket                  | minecraft-s3              |
| S3BackupPath          | Path within the S3 bucket              | world-backup              |
| RconPassword          | Password for RCON access               | (No default, required)    |
| BackupIntervalSeconds | Interval between backups               | 3600 (1 hour)             |
| IdleThresholdSeconds  | Time before shutting down when idle    | 1800 (30 minutes)         |
| CheckIntervalSeconds  | Interval between player checks         | 300 (5 minutes)           |
| MinecraftServerMemory | Memory for the task                    | 4096 MB                   |
| MinecraftServerCpu    | CPU units for the task                 | 2048                      |

### Deployment

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

## Scripts

### start-minecraft-server.sh

This script starts the Minecraft server ECS task. It requires the subnet ID and security group ID, which can be obtained from the CloudFormation stack outputs.

```bash
./start-minecraft-server.sh \
  --subnet-id subnet-xxxxxxxx \
  --security-group-id sg-xxxxxxxx
```

Options:
- `--cluster-name NAME`: ECS cluster name (default: minecraft-server-cluster)
- `--task-definition NAME`: ECS task definition name (default: minecraft-server-task-def)
- `--subnet-id ID`: Subnet ID for the task (required)
- `--security-group-id ID`: Security group ID for the task (required)
- `--help`: Display help message

### stop-minecraft-server.sh

This script stops the Minecraft server ECS task. If no task ARN or ID is provided, it lists running tasks and prompts you to select one.

```bash
./stop-minecraft-server.sh
```

Options:
- `--cluster-name NAME`: ECS cluster name (default: minecraft-server-cluster)
- `--task-arn ARN`: ECS task ARN (optional)
- `--task-id ID`: ECS task ID (optional)
- `--help`: Display help message

## Notes

- The CloudFormation template is designed to work alongside existing infrastructure without conflicts.
- The CIDR block (10.2.0.0/28) is chosen to avoid conflicts with existing VPCs.
- The scripts require the AWS CLI to be installed and configured with appropriate credentials.
- When the server is stopped, it will perform a final backup to S3 before shutting down.