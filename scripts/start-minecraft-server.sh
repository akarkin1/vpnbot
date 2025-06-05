#!/bin/bash
set -e

# Configuration
CLUSTER_NAME="minecraft-server-cluster"
TASK_DEFINITION="minecraft-server-task-def"
SUBNET_ID=""  # Will be populated from CloudFormation output
SECURITY_GROUP_ID=""  # Will be populated from CloudFormation output

# Function to display usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --cluster-name NAME       ECS cluster name (default: $CLUSTER_NAME)"
    echo "  --task-definition NAME    ECS task definition name (default: $TASK_DEFINITION)"
    echo "  --subnet-id ID            Subnet ID for the task (required if not set in script)"
    echo "  --security-group-id ID    Security group ID for the task (required if not set in script)"
    echo "  --help                    Display this help message"
    exit 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        --cluster-name)
            CLUSTER_NAME="$2"
            shift 2
            ;;
        --task-definition)
            TASK_DEFINITION="$2"
            shift 2
            ;;
        --subnet-id)
            SUBNET_ID="$2"
            shift 2
            ;;
        --security-group-id)
            SECURITY_GROUP_ID="$2"
            shift 2
            ;;
        --help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

# Check if subnet ID and security group ID are provided
if [ -z "$SUBNET_ID" ]; then
    echo "Error: Subnet ID is required. Use --subnet-id option or edit the script."
    exit 1
fi

if [ -z "$SECURITY_GROUP_ID" ]; then
    echo "Error: Security group ID is required. Use --security-group-id option or edit the script."
    exit 1
fi

# Get the latest task definition revision
TASK_DEFINITION_ARN=$(aws ecs describe-task-definition --task-definition $TASK_DEFINITION --query 'taskDefinition.taskDefinitionArn' --output text)

if [ -z "$TASK_DEFINITION_ARN" ]; then
    echo "Error: Could not find task definition: $TASK_DEFINITION"
    exit 1
fi

echo "Starting Minecraft server task..."
echo "Cluster: $CLUSTER_NAME"
echo "Task Definition: $TASK_DEFINITION_ARN"
echo "Subnet: $SUBNET_ID"
echo "Security Group: $SECURITY_GROUP_ID"

# Run the ECS task
TASK_ARN=$(aws ecs run-task \
    --cluster $CLUSTER_NAME \
    --task-definition $TASK_DEFINITION_ARN \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ID],securityGroups=[$SECURITY_GROUP_ID],assignPublicIp=ENABLED}" \
    --query 'tasks[0].taskArn' \
    --output text)

if [ -z "$TASK_ARN" ]; then
    echo "Error: Failed to start the task."
    exit 1
fi

echo "Task started successfully: $TASK_ARN"

# Wait for the task to reach running state
echo "Waiting for task to reach running state..."
aws ecs wait tasks-running \
    --cluster $CLUSTER_NAME \
    --tasks $TASK_ARN

# Get the public IP address of the task
echo "Getting task details..."
NETWORK_INTERFACE_ID=$(aws ecs describe-tasks \
    --cluster $CLUSTER_NAME \
    --tasks $TASK_ARN \
    --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
    --output text)

if [ -z "$NETWORK_INTERFACE_ID" ]; then
    echo "Error: Could not get network interface ID for the task."
    exit 1
fi

PUBLIC_IP=$(aws ec2 describe-network-interfaces \
    --network-interface-ids $NETWORK_INTERFACE_ID \
    --query 'NetworkInterfaces[0].Association.PublicIp' \
    --output text)

if [ -z "$PUBLIC_IP" ]; then
    echo "Error: Could not get public IP address for the task."
    exit 1
fi

echo "Minecraft server is starting at: $PUBLIC_IP"
echo "It may take a few minutes for the server to fully initialize."
echo "Connect to the server using the address: $PUBLIC_IP:25565"
echo "Task ARN: $TASK_ARN"