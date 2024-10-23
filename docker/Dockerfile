# Use Amazon Linux 2 as the base image
FROM amazonlinux:2

# Install Tailscale
RUN yum update -y && \
    yum install -y yum-utils && \
    yum-config-manager --add-repo https://pkgs.tailscale.com/stable/amazon-linux/2/tailscale.repo && \
    yum install -y tailscale aws-cli && \
    yum clean all

# Create a script to monitor connections
COPY ./scripts/monitor_connections.sh /usr/local/bin/monitor_connections.sh
COPY ./scripts/start_tailscale.sh /usr/local/bin/start_tailscale.sh
RUN chmod +x /usr/local/bin/monitor_connections.sh /usr/local/bin/start_tailscale.sh

# Start the Tailscale process and monitor connections
CMD ["/usr/local/bin/start_tailscale.sh"]