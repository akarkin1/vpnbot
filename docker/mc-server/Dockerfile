FROM itzg/minecraft-server:latest

# Install AWS CLI for S3 operations and mcrcon for server management
RUN apt-get update && \
    apt-get install -y \
    python3 \
    python3-pip \
    mcrcon \
    && pip3 install awscli && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables for Minecraft server configuration
ENV EULA=TRUE \
    TYPE=VANILLA \
    ENABLE_RCON=true \
    RCON_PORT=25575 \
    ENFORCE_WHITELIST=TRUE

# Copy scripts
COPY ./scripts/ /scripts/
RUN chmod +x /scripts/*.sh

# Set the entrypoint to our custom startup script
ENTRYPOINT ["/scripts/entrypoint.sh"]
