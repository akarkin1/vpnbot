FROM itzg/minecraft-server:latest

# Install AWS CLI for S3 operations and mcrcon for server management
RUN apt-get update && \
    apt-get install -y \
    python3 \
    python3-pip \
    git \
    build-essential \
    && git clone https://github.com/Tiiffi/mcrcon.git /opt/mcrcon \
    && cd /opt/mcrcon \
    && make && make install \
    && pip3 install awscli --break-system-packages && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables for Minecraft server configuration
ENV EULA=TRUE \
    TYPE=VANILLA \
    ENABLE_RCON=true \
    RCON_PORT=25575 \
    ENFORCE_WHITELIST=TRUE \
    WHITELIST=MartyByrde \
    OPS=MartyByrde

# Copy scripts
COPY ./scripts/ /scripts/
RUN chmod +x /scripts/*.sh

# Run as root to ensure write access to /data
USER root

# Set the entrypoint to our custom startup script
ENTRYPOINT ["/scripts/entrypoint.sh"]
