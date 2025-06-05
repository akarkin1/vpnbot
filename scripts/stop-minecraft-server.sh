#!/bin/bash
set -e

# Configuration
CLUSTER_NAME="minecraft-server-cluster"

# Function to display usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --cluster-name NAME       ECS cluster name (default: $CLUSTER_NAME)"
    echo "  --task-arn ARN            ECS task ARN (required if --task-id not provided)"
    echo "  --task-id ID              ECS task ID (required if --task-arn not provided)"
    echo "  --help                    Display this help message"
    exit 1
}

# Parse command line arguments
TASK_ARN=""
TASK_ID=""

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        --cluster-name)
            CLUSTER_NAME="$2"
            shift 2
            ;;
        --task-arn)
            TASK_ARN="$2"
            shift 2
            ;;
        --task-id)
            TASK_ID="$2"
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

# If task ID is provided but not the full ARN, construct the ARN
if [ -z "$TASK_ARN" ] && [ -n "$TASK_ID" ]; then
    # Get the AWS region and account ID
    REGION=$(aws configure get region)
    if [ -z "$REGION" ]; then
        echo "Error: AWS region not configured. Please run 'aws configure' or set the AWS_REGION environment variable."
        exit 1
    fi
    
    ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
    if [ -z "$ACCOUNT_ID" ]; then
        echo "Error: Could not determine AWS account ID."
        exit 1
    fi
    
    TASK_ARN="arn:aws:ecs:${REGION}:${ACCOUNT_ID}:task/${CLUSTER_NAME}/${TASK_ID}"
fi

# If no task ARN or ID is provided, list running tasks and prompt user to select one
if [ -z "$TASK_ARN" ]; then
    echo "No task ARN or ID provided. Listing running tasks in cluster $CLUSTER_NAME..."
    
    TASKS=$(aws ecs list-tasks --cluster $CLUSTER_NAME --query 'taskArns' --output text)
    
    if [ -z "$TASKS" ]; then
        echo "No running tasks found in cluster $CLUSTER_NAME."
        exit 0
    fi
    
    # Display tasks and prompt for selection
    echo "Running tasks:"
    TASK_ARRAY=($TASKS)
    for i in "${!TASK_ARRAY[@]}"; do
        echo "$((i+1)). ${TASK_ARRAY[$i]}"
    done
    
    echo -n "Enter the number of the task to stop (1-${#TASK_ARRAY[@]}): "
    read TASK_NUM
    
    if ! [[ "$TASK_NUM" =~ ^[0-9]+$ ]] || [ "$TASK_NUM" -lt 1 ] || [ "$TASK_NUM" -gt "${#TASK_ARRAY[@]}" ]; then
        echo "Invalid selection."
        exit 1
    fi
    
    TASK_ARN="${TASK_ARRAY[$((TASK_NUM-1))]}"
fi

echo "Stopping Minecraft server task: $TASK_ARN"

# Stop the ECS task
aws ecs stop-task \
    --cluster $CLUSTER_NAME \
    --task $TASK_ARN \
    --reason "Manually stopped by user"

echo "Task stop request sent. The task will perform a final backup before shutting down."
echo "You can monitor the task status with:"
echo "aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks $TASK_ARN"